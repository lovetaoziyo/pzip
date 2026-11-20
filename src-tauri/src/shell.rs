//! Windows Explorer right-click integration via HKCU registry keys.
//! HKCU (not HKLM) means no elevation is required.

use winreg::enums::HKEY_CURRENT_USER;
use winreg::RegKey;

const ADD_KEY: &str = r"Software\Classes\*\shell\PZip";
const DIR_KEY: &str = r"Software\Classes\Directory\shell\PZip";

const ARCHIVE_EXTS: &[&str] = &[
    "7z", "zip", "rar", "tar", "gz", "bz2", "xz", "wim", "iso", "cab", "arj", "lzh", "z", "tgz",
    "tbz", "txz", "7z.001", "zip.001",
];

pub fn register() -> Result<(), String> {
    let exe = std::env::current_exe()
        .map_err(|e| e.to_string())?
        .to_string_lossy()
        .to_string();

    // "Add to archive" for files and folders.
    set_value(ADD_KEY, "", "添加到压缩包 (PZip)")?;
    set_value(ADD_KEY, "Icon", &exe)?;
    set_value(&format!("{ADD_KEY}\\command"), "", &format!("\"{exe}\" --add \"%1\""))?;

    set_value(DIR_KEY, "", "添加到压缩包 (PZip)")?;
    set_value(DIR_KEY, "Icon", &exe)?;
    set_value(&format!("{DIR_KEY}\\command"), "", &format!("\"{exe}\" --add \"%1\""))?;

    // "Extract here" / "Extract to folder" on archive files.
    for ext in ARCHIVE_EXTS {
        let base = format!(r"Software\Classes\.{ext}\shell");
        set_value(&format!("{base}\\PZipExtractHere"), "", "解压到当前文件夹 (PZip)")?;
        set_value(&format!("{base}\\PZipExtractHere"), "Icon", &exe)?;
        set_value(
            &format!("{base}\\PZipExtractHere\\command"),
            "",
            &format!("\"{exe}\" --extract-here \"%1\""),
        )?;

        set_value(&format!("{base}\\PZipExtractTo"), "", "解压到文件夹 (PZip)")?;
        set_value(&format!("{base}\\PZipExtractTo"), "Icon", &exe)?;
        set_value(
            &format!("{base}\\PZipExtractTo\\command"),
            "",
            &format!("\"{exe}\" --extract-to \"%1\""),
        )?;
    }
    Ok(())
}

pub fn unregister() -> Result<(), String> {
    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let _ = hkcu.delete_subkey_all(ADD_KEY);
    let _ = hkcu.delete_subkey_all(DIR_KEY);
    for ext in ARCHIVE_EXTS {
        for name in ["PZipExtractHere", "PZipExtractTo"] {
            let key = format!(r"Software\Classes\.{ext}\shell\{name}");
            let _ = hkcu.delete_subkey_all(&key);
        }
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
