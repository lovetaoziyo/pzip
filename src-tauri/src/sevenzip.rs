//! Wrapper around the bundled official 7-Zip console binary (7z.exe + 7z.dll).
//! 7z.exe with 7z.dll is the FULL console version from the main 7-Zip package:
//! it supports every pack/unpack format that 7-Zip supports (RAR, WIM, ISO, CAB,
//! 7z, ZIP, ...). 7z.sfx (also bundled) enables self-extracting archives.

use std::io::Read;
use std::path::PathBuf;
use std::process::{Command, Stdio};

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, Manager};

/// Event emitted for every progress update while an operation runs.
pub const PROGRESS_EVENT: &str = "archive://progress";

#[derive(Clone, Default, Serialize)]
pub struct ArchiveEntry {
    pub path: String,
    pub size: u64,
    pub packed_size: u64,
    pub modified: String,
    pub attributes: String,
    pub encrypted: bool,
    pub folder: bool,
    pub method: String,
    pub crc: String,
}

#[derive(Clone, Serialize)]
pub struct ProgressInfo {
    pub percent: u8,
    pub file: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CompressOptions {
    /// Archive format: "7z", "zip", "tar", "wim", "xz", "bzip2", "gzip", ...
    pub format: String,
    /// Compression level 0..=9
    pub level: u8,
    /// Optional compression method, e.g. "LZMA2", "LZMA", "PPMd", "BZip2", "Deflate"
    pub method: Option<String>,
    pub password: Option<String>,
    /// Encrypt file names too (7z format only)
    pub encrypt_headers: bool,
    /// Optional volume size, e.g. "10m", "650m", "1000k"
    pub split: Option<String>,
    /// Create a self-extracting .exe (7z format)
    pub sfx: bool,
}

pub struct SevenZip {
    bin: PathBuf,
}

impl SevenZip {
    pub fn new(app: &AppHandle) -> Result<Self, String> {
        let resource_dir = app.path().resource_dir().map_err(|e| e.to_string())?;
        let manifest_dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
        let candidates = [
            resource_dir.join("binaries").join("7z.exe"),
            resource_dir.join("7z.exe"),
            manifest_dir.join("binaries").join("7z.exe"),
        ];
        let bin = candidates
            .into_iter()
            .find(|p| p.is_file())
            .ok_or_else(|| "bundled 7z.exe not found".to_string())?;
        Ok(Self { bin })
    }

    fn base(&self) -> Command {
        let mut c = Command::new(&self.bin);
        // 7-Zip must resolve 7z.sfx / 7zCon.sfx from its own directory.
        if let Some(dir) = self.bin.parent() {
            c.current_dir(dir);
        }
        // Force UTF-8 console output so non-ASCII paths survive the pipe.
        c.arg("-sccUTF-8");
        c
    }

    fn run(&self, args: &[String]) -> Result<(String, String, i32), String> {
        let out = self
            .base()
            .args(args)
            .output()
            .map_err(|e| format!("failed to launch 7z: {e}"))?;
        let stdout = String::from_utf8_lossy(&out.stdout).to_string();
        let stderr = String::from_utf8_lossy(&out.stderr).to_string();
        Ok((stdout, stderr, out.status.code().unwrap_or(-1)))
    }

    /// Run 7-Zip while streaming progress lines to the frontend.
    fn run_stream(&self, app: &AppHandle, args: &[String]) -> Result<(String, i32), String> {
        let mut child = self
            .base()
            .args(args)
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()
            .map_err(|e| format!("failed to launch 7z: {e}"))?;

        let mut stdout = child.stdout.take().unwrap();
        let mut stderr = child.stderr.take().unwrap();

        // Collect stderr on a background thread while we stream stdout.
        let stderr_thread = std::thread::spawn(move || {
            let mut buf = Vec::new();
            let _ = std::io::Read::read_to_end(&mut stderr, &mut buf);
            String::from_utf8_lossy(&buf).to_string()
        });

        // 7-Zip refreshes its progress line with '\r' (not '\n'), so we split
        // on both carriage-return and line-feed to get smooth progress updates.
        let mut pending: Vec<u8> = Vec::new();
        let mut chunk = [0u8; 4096];
        loop {
            let n = stdout.read(&mut chunk).map_err(|e| e.to_string())?;
            if n == 0 {
                break;
            }
            for &b in &chunk[..n] {
                if b == b'\n' || b == b'\r' {
                    if !pending.is_empty() {
                        let line = String::from_utf8_lossy(&pending);
                        if let Some(p) = parse_progress(&line) {
                            let _ = app.emit(PROGRESS_EVENT, &p);
                        }
                        pending.clear();
                    }
                } else {
                    pending.push(b);
                }
            }
        }
        if !pending.is_empty() {
            let line = String::from_utf8_lossy(&pending);
            if let Some(p) = parse_progress(&line) {
                let _ = app.emit(PROGRESS_EVENT, &p);
            }
        }

        let status = child.wait().map_err(|e| e.to_string())?;
        let code = status.code().unwrap_or(-1);
        let err = stderr_thread.join().unwrap_or_default();
        Ok((err, code))
    }

    pub fn version(&self) -> Result<String, String> {
        let (out, _, _) = self.run(&[])?;
        Ok(out.lines().next().unwrap_or("7-Zip").trim().to_string())
    }

    pub fn list(&self, archive: &str, password: Option<&str>) -> Result<Vec<ArchiveEntry>, String> {
        let mut args = vec!["l".to_string(), "-slt".to_string(), "-ba".to_string(), archive.to_string()];
        push_password(&mut args, password);
        let (out, err, code) = self.run(&args)?;
        if code != 0 {
            return Err(clean_err(&err));
        }
        Ok(parse_listing(&out))
    }

    pub fn compress(
        &self,
        app: &AppHandle,
        archive: &str,
        inputs: &[String],
        opts: &CompressOptions,
    ) -> Result<(), String> {
        let mut args: Vec<String> = vec!["a".to_string(), archive.to_string()];
        args.extend(inputs.iter().cloned());
        args.push(format!("-t{}", opts.format));
        args.push(format!("-mx={}", opts.level));
        if let Some(m) = &opts.method {
            if !m.is_empty() {
                args.push(format!("-m0={m}"));
            }
        }
        push_password(&mut args, opts.password.as_deref());
        if opts.encrypt_headers {
            args.push("-mhe".to_string());
        }
        if let Some(v) = &opts.split {
            if !v.is_empty() {
                args.push(format!("-v{v}"));
            }
        }
        if opts.sfx {
            args.push("-sfx".to_string());
        }
        args.push("-y".to_string());
        args.push("-bsp1".to_string());

        let (err, code) = self.run_stream(app, &args)?;
        if code != 0 {
            Err(clean_err(&err))
        } else {
            Ok(())
        }
    }

    pub fn extract(
        &self,
        app: &AppHandle,
        archive: &str,
        dest: &str,
        password: Option<&str>,
        overwrite: bool,
    ) -> Result<(), String> {
        let mut args = vec!["x".to_string(), archive.to_string(), format!("-o{dest}")];
        push_password(&mut args, password);
        args.push(if overwrite { "-aoa".to_string() } else { "-aos".to_string() });
        args.push("-y".to_string());
        args.push("-bsp1".to_string());

        let (err, code) = self.run_stream(app, &args)?;
        if code != 0 {
            Err(clean_err(&err))
        } else {
            Ok(())
        }
    }

    pub fn test(&self, app: &AppHandle, archive: &str, password: Option<&str>) -> Result<(), String> {
        let mut args = vec!["t".to_string(), archive.to_string()];
        push_password(&mut args, password);
        args.push("-y".to_string());
        args.push("-bsp1".to_string());

        let (err, code) = self.run_stream(app, &args)?;
        if code != 0 {
            Err(clean_err(&err))
        } else {
            Ok(())
        }
    }

    /// Compute checksums exactly like 7-Zip's "Checksum" feature (`7z h`).
    /// `algo` is one of: CRC32, CRC64, SHA1, SHA256, SHA384, SHA512, SHA3-256, ...
    pub fn hash(&self, paths: &[String], algo: &str) -> Result<String, String> {
        let mut args = vec!["h".to_string(), format!("-scrc{algo}")];
        args.extend(paths.iter().cloned());
        let (out, err, code) = self.run(&args)?;
        if code != 0 {
            return Err(clean_err(&err));
        }
        Ok(out)
    }

    /// Run the built-in compression benchmark (`7za b`).
    pub fn benchmark(&self) -> Result<String, String> {
        let (out, err, code) = self.run(&["b".to_string()])?;
        if code != 0 {
            return Err(clean_err(&err));
        }
        Ok(out)
    }
}

fn push_password(args: &mut Vec<String>, password: Option<&str>) {
    match password {
        Some(p) if !p.is_empty() => args.push(format!("-p{p}")),
        // Empty password: still pass "-p" so 7-Zip never prompts on stdin.
        _ => args.push("-p".to_string()),
    }
}

fn clean_err(err: &str) -> String {
    let line = err
        .lines()
        .filter(|l| !l.trim().is_empty())
        .last()
        .unwrap_or("unknown error")
        .trim()
        .to_string();
    if line.is_empty() {
        "operation failed".to_string()
    } else {
        line
    }
}

fn parse_progress(line: &str) -> Option<ProgressInfo> {
    let pct = line.find('%')?;
    let digits: String = line[..pct].chars().filter(|c| c.is_ascii_digit()).collect();
    if digits.is_empty() {
        return None;
    }
    let percent: u8 = digits.parse().ok()?;
    if percent > 100 {
        return None;
    }
    let rest = line[pct + 1..].trim_start();
    let file = match rest.find(" - ") {
        Some(i) => rest[i + 3..].trim().to_string(),
        None => String::new(),
    };
    Some(ProgressInfo { percent, file })
}

fn parse_listing(out: &str) -> Vec<ArchiveEntry> {
    let mut entries = Vec::new();
    let mut cur: Option<ArchiveEntry> = None;

    let mut finish = |cur: &mut Option<ArchiveEntry>, entries: &mut Vec<ArchiveEntry>| {
        if let Some(e) = cur.take() {
            entries.push(e);
        }
    };

    for raw in out.lines() {
        let line = raw.trim_end_matches('\r');
        if line.is_empty() {
            finish(&mut cur, &mut entries);
            continue;
        }
        if let Some((k, v)) = line.split_once('=') {
            let k = k.trim();
            let v = v.trim().to_string();
            match k {
                "Path" => {
                    let mut e = ArchiveEntry::default();
                    e.path = v;
                    cur = Some(e);
                }
                "Size" => {
                    if let Some(e) = cur.as_mut() {
                        e.size = v.parse().unwrap_or(0);
                    }
                }
                "Packed Size" => {
                    if let Some(e) = cur.as_mut() {
                        e.packed_size = v.parse().unwrap_or(0);
                    }
                }
                "Modified" => {
                    if let Some(e) = cur.as_mut() {
                        e.modified = v;
                    }
                }
                "Attributes" => {
                    if let Some(e) = cur.as_mut() {
                        e.folder = v.starts_with('D');
                        e.attributes = v;
                    }
                }
                "Encrypted" => {
                    if let Some(e) = cur.as_mut() {
                        e.encrypted = v == "+";
                    }
                }
                "Method" => {
                    if let Some(e) = cur.as_mut() {
                        e.method = v;
                    }
                }
                "CRC" => {
                    if let Some(e) = cur.as_mut() {
                        e.crc = v;
                    }
                }
                _ => {}
            }
        }
    }
    finish(&mut cur, &mut entries);
    entries
}
