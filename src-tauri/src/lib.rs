use tauri::Manager;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpListener;

// --- OAuth redirect port (must match frontend ANTIGRAVITY_OAUTH_REDIRECT_PORT) ---
const OAUTH_REDIRECT_PORT: u16 = 39171;

// --- Shared state for mobile OAuth result (populated by on_navigation) ---
static OAUTH_RESULT: std::sync::Mutex<Option<String>> = std::sync::Mutex::new(None);

// --- Desktop OAuth: TCP loopback listener ---

#[tauri::command]
async fn start_oauth_listener(port: u16) -> Result<String, String> {
    let addr = format!("127.0.0.1:{}", port);
    let listener = TcpListener::bind(&addr)
        .await
        .map_err(|e| format!("Failed to bind to {}: {}", addr, e))?;

    let (mut stream, _) = listener
        .accept()
        .await
        .map_err(|e| format!("Accept failed: {}", e))?;

    let mut buf = vec![0u8; 4096];
    let n = stream
        .read(&mut buf)
        .await
        .map_err(|e| format!("Read failed: {}", e))?;

    let request = String::from_utf8_lossy(&buf[..n]);
    let first_line = request.lines().next().unwrap_or("");
    let path = first_line
        .split_whitespace()
        .nth(1)
        .unwrap_or("/")
        .to_string();

    let html = concat!(
        r#"<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8">"#,
        r#"<meta name="viewport" content="width=device-width,initial-scale=1.0">"#,
        r#"<title>Lumi AI — Authentication</title><style>"#,
        r#"*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}"#,
        r#"body{font-family:"Segoe UI",system-ui,-apple-system,sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;background:#F8F9FF;color:#191C20;-webkit-font-smoothing:antialiased}"#,
        r#".card{text-align:center;padding:48px 40px;border-radius:32px;background:#FFF;box-shadow:0 2px 16px rgba(0,0,0,.08);max-width:380px;width:calc(100% - 48px);animation:card-in .4s cubic-bezier(.05,.7,.1,1)}"#,
        r#"@keyframes card-in{from{opacity:0;transform:scale(.94) translateY(8px)}to{opacity:1;transform:scale(1) translateY(0)}}"#,
        r#".icon{margin-bottom:16px}.icon svg{width:48px;height:48px}.icon-success svg{fill:#39608F}"#,
        r#"h2{font-size:22px;font-weight:500;margin-bottom:8px}p{font-size:14px;color:#43474E;line-height:1.5}.closing{margin-top:24px;font-size:12px;color:#73777F}"#,
        r#"@media(prefers-color-scheme:dark){body{background:#111418;color:#E1E2E8}.card{background:#1D2024;box-shadow:0 2px 16px rgba(0,0,0,.3)}.icon-success svg{fill:#A2C9FE}p{color:#C3C6CF}.closing{color:#8D9199}}"#,
        r#"</style></head><body><div class="card">"#,
        r#"<div class="icon icon-success"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960"><path d="M480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm-56-328L300-532l-56 56 180 180 344-344-56-56-288 288Z"/></svg></div>"#,
        r#"<h2>Authentication successful</h2>"#,
        r#"<p>You can close this tab and return to Lumi AI.</p>"#,
        r#"</div></body></html>"#,
    );
    let response = format!(
        "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        html.len(),
        html
    );
    let _ = stream.write_all(response.as_bytes()).await;
    let _ = stream.flush().await;
    drop(stream);

    let callback_url = format!("http://127.0.0.1:{}{}", port, path);
    Ok(callback_url)
}

// --- Mobile OAuth: retrieve intercepted callback URL ---

#[tauri::command]
fn get_oauth_result() -> Option<String> {
    OAUTH_RESULT.lock().ok().and_then(|mut guard| guard.take())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_http::init())
        .setup(|app| {
            let handle = app.handle().clone();

            let mut builder = tauri::WebviewWindowBuilder::new(
                app,
                "main",
                tauri::WebviewUrl::App("index.html".into()),
            );

            #[cfg(desktop)]
            {
                builder = builder
                    .title("Lumi AI")
                    .inner_size(1024.0, 700.0)
                    .min_inner_size(360.0, 480.0);
            }

            // Override the default Android WebView User-Agent to remove the
            // "; wv)" token.  Google blocks OAuth sign-in from embedded
            // WebViews by detecting this marker (403 disallowed_useragent).
            // A standard Chrome Mobile UA lets the in-WebView flow succeed.
            #[cfg(target_os = "android")]
            {
                builder = builder.user_agent(
                    "Mozilla/5.0 (Linux; Android 15) \
                     AppleWebKit/537.36 (KHTML, like Gecko) \
                     Chrome/131.0.6778.200 Mobile Safari/537.36",
                );
            }

            builder
                .on_navigation(move |url: &tauri::Url| {
                    let host = url.host_str().unwrap_or("");
                    let port = url.port().unwrap_or(0);
                    if (host == "127.0.0.1" || host == "localhost")
                        && port == OAUTH_REDIRECT_PORT
                    {
                        if let Ok(mut guard) = OAUTH_RESULT.lock() {
                            *guard = Some(url.to_string());
                        }
                        // Navigate back to the app.  On Android, forward
                        // navigation to https://tauri.localhost/ fails with
                        // ERR_CONNECTION_REFUSED because wry's custom-protocol
                        // shouldInterceptRequest handler runs on a background
                        // thread and doesn't reliably serve the asset after the
                        // WebView has visited an external origin.  Instead, use
                        // history.go() to jump back to the first history entry
                        // (the cached app page).  The session-history restore
                        // path avoids shouldInterceptRequest entirely.
                        // On desktop this handler is unlikely to fire (OAuth
                        // uses the system browser), but if it does, navigate()
                        // works fine there.
                        let h = handle.clone();
                        std::thread::spawn(move || {
                            std::thread::sleep(std::time::Duration::from_millis(300));
                            if let Some(ww) = h.get_webview_window("main") {
                                #[cfg(target_os = "android")]
                                {
                                    let _ = ww.eval(
                                        "window.history.go(-(window.history.length - 1))"
                                    );
                                }
                                #[cfg(not(target_os = "android"))]
                                {
                                    #[cfg(dev)]
                                    let app_url: tauri::Url = "http://localhost:1420"
                                        .parse()
                                        .expect("invalid dev URL");
                                    #[cfg(not(dev))]
                                    let app_url: tauri::Url = "https://tauri.localhost/"
                                        .parse()
                                        .expect("invalid production URL");
                                    let _ = ww.navigate(app_url);
                                }
                            }
                        });
                        return false;
                    }
                    true
                })
                .build()
                .expect("failed to build main window");

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            start_oauth_listener,
            get_oauth_result
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
