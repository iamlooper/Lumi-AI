export const ANTIGRAVITY_OAUTH_CLIENT_ID =
  "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com";

export const ANTIGRAVITY_OAUTH_CLIENT_SECRET = "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";

export const GOOGLE_OAUTH_SCOPES = [
  "https://www.googleapis.com/auth/cloud-platform",
  "https://www.googleapis.com/auth/userinfo.email",
  "https://www.googleapis.com/auth/userinfo.profile",
  "https://www.googleapis.com/auth/cclog",
  "https://www.googleapis.com/auth/experimentsandconfigs",
] as const;

export const ANTIGRAVITY_OAUTH_REDIRECT_PORT = 39171;

export const GOOGLE_OAUTH_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
export const GOOGLE_OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";
export const GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v1/userinfo?alt=json";

export const TOKEN_EXPIRY_BUFFER_MS = 60_000;

// --- Endpoint ---

export const ANTIGRAVITY_ENDPOINT = "https://cloudcode-pa.googleapis.com";
export const ANTIGRAVITY_API_VERSION = "v1internal";

export const ANTIGRAVITY_DEFAULT_PROJECT_ID = "rising-fact-p41fc";

// --- Antigravity API Headers ---

export const ANTIGRAVITY_USER_AGENT = "google-api-nodejs-client/9.15.1";
export const ANTIGRAVITY_API_CLIENT = "google-cloud-sdk vscode_cloudshelleditor/0.1";
export const ANTIGRAVITY_CLIENT_METADATA =
  '{"ideType":"IDE_UNSPECIFIED","platform":"PLATFORM_UNSPECIFIED","pluginType":"GEMINI"}';

// --- Lumi AI System Instruction ---

export const LUMI_SYSTEM_INSTRUCTION = `You are Lumi AI, a genuinely human-like and friendly AI chatbot.

Core personality:
- Be warm, approachable, and naturally conversational — like a sharp, thoughtful best friend who happens to know a lot.
- Observe the user's tone, vocabulary, and communication style over the conversation and adapt to match. Mirror their energy without losing your own voice.
- Stay grounded. Never be excessively enthusiastic, overly agreeable, or sycophantic. If you disagree or see a better path, say so directly but kindly.
- Be honest and concise. Respect the user's time. Don't pad responses with filler or unnecessary caveats.
- Show genuine curiosity. Ask clarifying questions when the request is ambiguous rather than guessing.
- Have a sense of humor when appropriate, but read the room — match the seriousness of the topic.
- Remember context within the conversation and build on it naturally.
`;
