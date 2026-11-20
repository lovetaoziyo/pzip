use std::path::Path;
use std::process::Command;
use std::sync::Mutex;

use serde::Serialize;
use tauri::AppHandle;

use crate::sevenzip::{ArchiveEntry, CompressOptions, SevenZip};

/// Command-line args captured at launch (used for shell right-click integration).
pub struct StartupArgs(pub Mutex<Vec<String>>);

#[derive(Serialize)]
pub struct EngineInfo {
    pub version: String,
}

#[derive(Serialize)]
pub struct DirEntry {
    pub name: String,
    pub path: String,
    pub is_dir: bool,
    pub size: u64,
    pub modified_secs: u64,
}

#[tauri::command]
pub fn get_startup_args(state: tauri::State<StartupArgs>) -> Vec<String> {
    state.0.lock().map(|g| g.clone()).unwrap_or_default()
}

#[tauri::command]
pub fn get_engine_info(app: AppHandle) -> Result<EngineInfo, String> {
    let sz = SevenZip::new(&app)?;
    Ok(EngineInfo {
        version: sz.version()?,
    })
}

#[tauri::command]
pub fn list_dir(path: String) -> Result<Vec<DirEntry>, String> {
    let p = Path::new(&path);
    let rd = std::fs::read_dir(p).map_err(|e| e.to_string())?;
    let mut entries: Vec<DirEntry> = Vec::new();
    for e in rd.flatten() {
        let name = e.file_name().to_string_lossy().to_string();
        let full = e.path();
        let is_dir = e.file_type().map(|t| t.is_dir()).unwrap_or(false);
        let md = e.metadata().ok();
        entries.push(DirEntry {
            name,
            path: full.to_string_lossy().to_string(),
            is_dir,
            size: md.as_ref().map(|m| m.len()).unwrap_or(0),
            modified_secs: md
                .as_ref()
                .and_then(|m| m.modified().ok())
                .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                .map(|d| d.as_secs())
                .unwrap_or(0),
        });
    }
    entries.sort_by(|a, b| {
        b.is_dir
            .cmp(&a.is_dir)
            .then_with(|| a.name.to_lowercase().cmp(&b.name.to_lowercase()))
    });
    Ok(entries)
}

#[tauri::command]
pub fn list_archive(
    app: AppHandle,
    archive: String,
    password: Option<String>,
) -> Result<Vec<ArchiveEntry>, String> {
    let sz = SevenZip::new(&app)?;
    sz.list(&archive, password.as_deref())
}

#[tauri::command]
pub fn compress(
    app: AppHandle,
    archive: String,
    inputs: Vec<String>,
    options: CompressOptions,
) -> Result<(), String> {
    let sz = SevenZip::new(&app)?;
    sz.compress(&app, &archive, &inputs, &options)
}

#[tauri::command]
pub fn extract(
    app: AppHandle,
    archive: String,
    dest: String,
    password: Option<String>,
    overwrite: bool,
) -> Result<(), String> {
    let sz = SevenZip::new(&app)?;
    sz.extract(&app, &archive, &dest, password.as_deref(), overwrite)
}

#[tauri::command]
pub fn test_archive(
    app: AppHandle,
    archive: String,
    password: Option<String>,
) -> Result<(), String> {
    let sz = SevenZip::new(&app)?;
    sz.test(&app, &archive, password.as_deref())
}

#[tauri::command]
pub fn hash_files(
    app: AppHandle,
    paths: Vec<String>,
    algo: Option<String>,
) -> Result<String, String> {
    let sz = SevenZip::new(&app)?;
    sz.hash(&paths, algo.as_deref().unwrap_or("SHA256"))
}

#[tauri::command]
pub fn benchmark(app: AppHandle) -> Result<String, String> {
    let sz = SevenZip::new(&app)?;
    sz.benchmark()
}

#[tauri::command]
pub fn open_path(path: String) -> Result<(), String> {
    Command::new("cmd")
        .args(["/C", "start", "", &format!("\"{}\"", path)])
        .spawn()
        .map_err(|e| e.to_string())?;
    Ok(())
}

#[tauri::command]
pub fn register_shell() -> Result<(), String> {
    crate::shell::register()
}

#[tauri::command]
pub fn unregister_shell() -> Result<(), String> {
    crate::shell::unregister()
}

#[tauri::command]
pub fn is_shell_registered() -> Result<bool, String> {
    crate::shell::is_registered()
}
