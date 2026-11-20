mod commands;
mod sevenzip;
mod shell;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let startup = commands::StartupArgs(std::sync::Mutex::new(
        std::env::args().skip(1).collect(),
    ));
    tauri::Builder::default()
        .manage(startup)
        .plugin(tauri_plugin_dialog::init())
        .invoke_handler(tauri::generate_handler![
            commands::get_engine_info,
            commands::get_startup_args,
            commands::list_dir,
            commands::list_archive,
            commands::compress,
            commands::extract,
            commands::test_archive,
            commands::hash_files,
            commands::benchmark,
            commands::open_path,
            commands::register_shell,
            commands::unregister_shell,
            commands::is_shell_registered
        ])
        .run(tauri::generate_context!())
        .expect("error while running PZip");
}
