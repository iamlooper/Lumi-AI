import { Show } from "solid-js";
import { login, authLoading, authError } from "../lib/stores/auth";
import "./LoginScreen.css";

export default function LoginScreen() {
  let dialogRef!: HTMLDialogElement;

  const openDisclaimer = () => dialogRef.showModal();
  const closeDisclaimer = () => dialogRef.close();

  const handleLogin = async () => {
    try {
      await login();
    } catch {
      // Error is set in the store
    }
  };

  return (
    <div class="login-screen">
      <div class="login-card">
        <md-elevation></md-elevation>
        <div class="login-logo">
          <div class="login-icon lumi-logo" />
        </div>
        <h1 class="md-typescale-display-small login-title">Lumi AI</h1>
        <p class="md-typescale-body-large login-subtitle">
          Your friendly AI chatbot powered by Gemini
        </p>

        <Show when={authError()}>
          <div class="login-error md-typescale-body-medium">
            {authError()}
          </div>
        </Show>

        <md-filled-button
          onClick={handleLogin}
          disabled={authLoading()}
          class="login-button"
        >
          <Show when={!authLoading()} fallback={<md-circular-progress indeterminate style={{ "--md-circular-progress-size": "20px" }}></md-circular-progress>}>
            <md-icon slot="icon">login</md-icon>
            Sign in with Google
          </Show>
        </md-filled-button>

        <p class="md-typescale-body-small login-disclaimer">
          Authenticates via Google{" "}
          <a href="https://antigravity.google" target="_blank" rel="noopener noreferrer" class="login-link">
            Antigravity
          </a>{" "}
          OAuth
        </p>
        <p class="md-typescale-body-small login-disclaimer">
          Please review the{" "}
          <a class="login-link" onClick={openDisclaimer}>
            disclaimer
          </a>{" "}
          before proceeding
        </p>
      </div>

      <dialog
        ref={dialogRef}
        class="disclaimer-dialog"
        onClick={(e) => { if (e.target === dialogRef) closeDisclaimer(); }}
      >
        <div class="disclaimer-dialog-container">
          <h2 class="disclaimer-dialog-headline md-typescale-headline-small">Disclaimer</h2>
          <div class="disclaimer-dialog-body">
            <div class="disclaimer-section">
              <h3 class="disclaimer-heading md-typescale-title-small">Assume All Risk</h3>
              <p class="md-typescale-body-medium">
                Use of this software may conflict with Google's Terms of Service. Accounts — particularly
                new or recently created ones — may face restrictions, suspension, or shadow-banning
                irrespective of subscription tier. We strongly recommend against using your primary Google
                account. Choose an account that does not hold critical data or services you depend on.
              </p>
            </div>
            <div class="disclaimer-section">
              <h3 class="disclaimer-heading md-typescale-title-small">Independent Project</h3>
              <p class="md-typescale-body-medium">
                This is an independent open-source project. It is not endorsed by, sponsored by,
                or affiliated with Google LLC in any capacity. "Antigravity", "Gemini",
                "Google Cloud", and "Google" are registered trademarks of Google LLC.
              </p>
            </div>
            <div class="disclaimer-section">
              <h3 class="disclaimer-heading md-typescale-title-small">No Warranty</h3>
              <p class="md-typescale-body-medium">
                This software is distributed on an "as is" basis without warranties of any kind,
                express or implied. You bear sole responsibility for ensuring your use complies
                with all applicable Terms of Service and Acceptable Use Policies.
              </p>
            </div>
          </div>
          <div class="disclaimer-dialog-actions">
            <button type="button" class="disclaimer-btn" onClick={closeDisclaimer}>Understood</button>
          </div>
        </div>
      </dialog>
    </div>
  );
}
