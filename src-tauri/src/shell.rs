//! Windows Explorer integration via HKCU registry keys (no elevation required):
//!   - classic context menu ("添加到压缩包" on files/folders, extract on archives)
//!   - file association (.7z/.zip/... double-click opens PZip)

use winreg::enums::HKEY_CURRENT_USER;
use winreg::RegKey;

const ADD_KEY: &str = r"Software\Classes\*\shell\PZip";
const DIR_KEY: &str = r"Software\Classes\Directory\shell\PZip";
const PROGID: &str = "PZip.Archive";

const ARCHIVE_EXTS: &[&str] = &[
    "7z", "zip", "rar", "tar", "gz", "bz2", "xz", "wim", "iso", "cab", "arj", "lzh", "z", "tgz",
    "tbz", "txz",
];

pub fn register() -> Result<(), String> {
    let exe = std::env::current_exe()
        .map_err(|e| e.to_string())?
        .to_string_lossy()
        .to_string();

    // 0. Record our own install path (the COM server reads this to launch us).
    set_value("Software\\PZip", "ExePath", &exe)?;

    // 1. "Add to archive" on files and folders.
    set_value(ADD_KEY, "", "添加到压缩包 (PZip)")?;
    set_value(ADD_KEY, "Icon", &exe)?;
    set_value(&format!("{ADD_KEY}\\command"), "", &format!("\"{exe}\" --add \"%1\""))?;

    set_value(DIR_KEY, "", "添加到压缩包 (PZip)")?;
    set_value(DIR_KEY, "Icon", &exe)?;
    set_value(&format!("{DIR_KEY}\\command"), "", &format!("\"{exe}\" --add \"%1\""))?;

    // 2. ProgID for archive files: open + extract context menu.
    let base = format!("Software\\Classes\\{PROGID}");
    set_value(&base, "", "PZip 压缩包")?;
    set_value(&format!("{base}\\DefaultIcon"), "", &format!("{exe},0"))?;
    set_value(&format!("{base}\\shell\\open\\command"), "", &format!("\"{exe}\" \"%1\""))?;

    set_value(&format!("{base}\\shell\\PZipExtractHere"), "", "解压到当前文件夹 (PZip)")?;
    set_value(&format!("{base}\\shell\\PZipExtractHere"), "Icon", &exe)?;
    set_value(
        &format!("{base}\\shell\\PZipExtractHere\\command"),
        "",
        &format!("\"{exe}\" --extract-here \"%1\""),
    )?;

    set_value(&format!("{base}\\shell\\PZipExtractTo"), "", "解压到文件夹 (PZip)")?;
    set_value(&format!("{base}\\shell\\PZipExtractTo"), "Icon", &exe)?;
    set_value(
        &format!("{base}\\shell\\PZipExtractTo\\command"),
        "",
        &format!("\"{exe}\" --extract-to \"%1\""),
    )?;

    // 3. Associate each archive extension with the ProgID.
    for ext in ARCHIVE_EXTS {
        set_value(&format!(r"Software\Classes\.{ext}"), "", PROGID)?;
    }
    Ok(())
}

pub fn unregister() -> Result<(), String> {
    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let _ = hkcu.delete_subkey_all(ADD_KEY);
    let _ = hkcu.delete_subkey_all(DIR_KEY);
    let _ = hkcu.delete_subkey_all(&format!("Software\\Classes\\{PROGID}"));
    for ext in ARCHIVE_EXTS {
        clear_default(&format!(r"Software\Classes\.{ext}"));
    }
    Ok(())
}

pub fn is_registered() -> Result<bool, String> {
    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    Ok(hkcu.open_subkey(ADD_KEY).is_ok())
}

fn set_value(key: &str, name: &str, value: &str) -> Result<(), String> {
    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let (k, _) = hkcu.create_subkey(key).map_err(|e| e.to_string())?;
    k.set_value(name, &value).map_err(|e| e.to_string())
}

fn clear_default(key: &str) {
    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    if let Ok(k) = hkcu.open_subkey(key) {
        let _ = k.delete_value("");
    }
}
