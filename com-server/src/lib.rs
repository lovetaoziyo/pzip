//! PZip Windows 11 context-menu COM server.
//!
//! Implements `IExplorerCommand` so PZip verbs appear in the *new* Windows 11
//! context menu (not just "Show more options"). The server is a surrogate COM
//! DLL registered through a sparse package (see ../sparse-package/).

use std::ffi::c_void;

use windows::core::{implement, GUID, IUnknown, Interface, PCWSTR, PWSTR, HRESULT};
use windows::Win32::Foundation::BOOL;
use windows::Win32::System::Com::{CoTaskMemAlloc, IBindCtx, IClassFactory, IClassFactory_Impl};
use windows::Win32::System::Registry::{RegGetValueW, HKEY_CURRENT_USER, RRF_RT_REG_SZ};
use windows::Win32::UI::Shell::{
    IEnumExplorerCommand, IExplorerCommand, IExplorerCommand_Impl, IShellItemArray,
    SIGDN_FILESYSPATH,
};

// One CLSID per verb (the manifest maps each Verb -> Clsid).
pub const CLSID_ADD: GUID = GUID::from_u128(0x9E1B2C3D_4A5B_4C5D_8E7F_1A2B3C4D5E6F);
pub const CLSID_EXTRACT_HERE: GUID = GUID::from_u128(0xA1F2E3D4_C5B6_4A78_9B0C_1D2E3F4A5B6C);
pub const CLSID_EXTRACT_TO: GUID = GUID::from_u128(0xB2A3C4D5_E6F7_4A88_9A1B_2C3D4E5F6A7B);

fn mode_for(clsid: &GUID) -> Option<&'static str> {
    if *clsid == CLSID_ADD {
        Some("--add")
    } else if *clsid == CLSID_EXTRACT_HERE {
        Some("--extract-here")
    } else if *clsid == CLSID_EXTRACT_TO {
        Some("--extract-to")
    } else {
        None
    }
}

fn alloc_pwstr(s: &str) -> PWSTR {
    let utf16: Vec<u16> = s.encode_utf16().chain(std::iter::once(0)).collect();
    let bytes = utf16.len() * std::mem::size_of::<u16>();
    let ptr = unsafe { CoTaskMemAlloc(bytes) } as *mut u16;
    unsafe {
        std::ptr::copy_nonoverlapping(utf16.as_ptr(), ptr, utf16.len());
    }
    PWSTR(ptr)
}

fn read_pzip_exe() -> Option<String> {
    // The app writes its own path here during shell registration.
    let mut buf = vec![0u16; 1024];
    let mut len = (buf.len() * std::mem::size_of::<u16>()) as u32;
    let hr = unsafe {
        RegGetValueW(
            HKEY_CURRENT_USER,
            windows::core::w!("Software\\PZip"),
            windows::core::w!("ExePath"),
            RRF_RT_REG_SZ,
            None,
            Some(buf.as_mut_ptr() as *mut c_void),
            Some(&mut len),
        )
    };
    if hr.is_err() {
        return None;
    }
    let chars = (len as usize / 2).saturating_sub(1).min(buf.len());
    String::from_utf16(&buf[..chars]).ok()
}

#[implement(IExplorerCommand)]
struct Command {
    mode: &'static str,
}

impl Command {
    fn new(mode: &'static str) -> Self {
        Self { mode }
    }

    fn title(&self) -> &'static str {
        match self.mode {
            "--add" => "添加到压缩包 (PZip)",
            "--extract-here" => "解压到当前文件夹 (PZip)",
            _ => "解压到文件夹 (PZip)",
        }
    }
}

impl IExplorerCommand_Impl for Command {
    fn GetTitle(&self, _items: Option<&IShellItemArray>) -> windows::core::Result<PWSTR> {
        Ok(alloc_pwstr(self.title()))
    }

    fn GetIcon(&self, _items: Option<&IShellItemArray>) -> windows::core::Result<PWSTR> {
        Ok(alloc_pwstr(""))
    }

    fn GetToolTip(&self, _items: Option<&IShellItemArray>) -> windows::core::Result<PWSTR> {
        Ok(alloc_pwstr(""))
    }

    fn GetCanonicalName(&self) -> windows::core::Result<GUID> {
        Err(windows::core::Error::from(HRESULT(0x80004001i32))) // E_NOTIMPL
    }

    fn GetState(
        &self,
        _items: Option<&IShellItemArray>,
        _ok: BOOL,
    ) -> windows::core::Result<u32> {
        Ok(0) // ECS_ENABLED
    }

    fn Invoke(
        &self,
        items: Option<&IShellItemArray>,
        _pbc: Option<&IBindCtx>,
    ) -> windows::core::Result<()> {
        let items =
            items.ok_or_else(|| windows::core::Error::from(HRESULT(0x80004005i32)))?; // E_FAIL
        let item = unsafe { items.GetItemAt(0)? };
        let path = unsafe { item.GetDisplayName(SIGDN_FILESYSPATH)? };
        let path_str = unsafe { path.to_string() }.unwrap_or_default();

        if let Some(exe) = read_pzip_exe() {
            let _ = std::process::Command::new(&exe)
                .arg(self.mode)
                .arg(&path_str)
                .spawn();
        }
        Ok(())
    }

    fn GetFlags(&self) -> windows::core::Result<u32> {
        Ok(0)
    }

    fn EnumSubCommands(&self) -> windows::core::Result<IEnumExplorerCommand> {
        Err(windows::core::Error::from(HRESULT(0x80004001i32))) // E_NOTIMPL
    }
}

#[implement(IClassFactory)]
struct Factory {
    mode: &'static str,
}

impl IClassFactory_Impl for Factory {
    fn CreateInstance(
        &self,
        _outer: Option<&IUnknown>,
        riid: *const GUID,
        ppv: *mut *mut c_void,
    ) -> windows::core::Result<()> {
        let cmd: IExplorerCommand = Command::new(self.mode).into();
        unsafe { cmd.query(riid, ppv).ok()? };
        Ok(())
    }

    fn LockServer(&self, _lock: BOOL) -> windows::core::Result<()> {
        Ok(())
    }
}

#[no_mangle]
pub extern "system" fn DllGetClassObject(
    clsid: *const GUID,
    iid: *const GUID,
    ppv: *mut *mut c_void,
) -> HRESULT {
    unsafe {
        let clsid = *clsid;
        let Some(mode) = mode_for(&clsid) else {
            return HRESULT(0x80040111u32 as i32); // CLASS_E_CLASSNOTAVAILABLE
        };
        let factory: IClassFactory = Factory { mode }.into();
        factory.query(iid, ppv)
    }
}

#[no_mangle]
pub extern "system" fn DllCanUnloadNow() -> HRESULT {
    HRESULT(0) // S_OK
}
