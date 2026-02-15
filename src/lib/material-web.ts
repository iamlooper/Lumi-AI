// --- Material Web Component Imports ---
// Import only the components we need to minimize bundle size.

import "@material/web/button/filled-button.js";
import "@material/web/icon/icon.js";
import "@material/web/iconbutton/icon-button.js";
import "@material/web/iconbutton/filled-tonal-icon-button.js";
import "@material/web/divider/divider.js";
import "@material/web/progress/circular-progress.js";
import "@material/web/elevation/elevation.js";

import { styles as typescaleStyles } from "@material/web/typography/md-typescale-styles.js";
document.adoptedStyleSheets.push(typescaleStyles.styleSheet!);
