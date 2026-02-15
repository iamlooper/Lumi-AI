import "solid-js";

declare module "solid-js" {
  namespace JSX {
    interface IntrinsicElements {
      "md-icon": any;
      "md-elevation": any;
      "md-filled-button": any;
      "md-icon-button": any;
      "md-filled-tonal-icon-button": any;
      "md-divider": any;
      "md-circular-progress": any;
    }
  }
}
