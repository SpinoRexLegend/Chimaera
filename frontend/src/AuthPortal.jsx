import { useState } from "react";
import { motion } from "framer-motion";
import { isSupabaseConfigured, supabase } from "./supabase";

const initialProfile = {
  displayName: "",
  institution: "",
  bio: "",
  skills: "",
  interests: "",
  availabilityStatus: "AVAILABLE",
  profileVisibility: "MEMBERS",
  timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
};

function splitList(value) {
  return value.split(",").map((item) => item.trim()).filter(Boolean);
}

export default function AuthPortal({ onAuthenticated }) {
  const [mode, setMode] = useState("register");
  const [credentials, setCredentials] = useState({ email: "", password: "" });
  const [profile, setProfile] = useState(initialProfile);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  const updateCredentials = (key) => (event) =>
    setCredentials((current) => ({ ...current, [key]: event.target.value }));
  const updateProfile = (key) => (event) =>
    setProfile((current) => ({ ...current, [key]: event.target.value }));

  async function submit(event) {
    event.preventDefault();
    if (!supabase) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      if (mode === "register") {
        const profilePayload = {
          ...profile,
          skills: splitList(profile.skills),
          interests: splitList(profile.interests),
        };
        const { data, error: authError } = await supabase.auth.signUp({
          email: credentials.email.trim(),
          password: credentials.password,
          options: {
            emailRedirectTo: window.location.origin,
            data: {
              display_name: profile.displayName.trim(),
              institution: profile.institution.trim(),
              bio: profile.bio.trim(),
              skills: profilePayload.skills,
              interests: profilePayload.interests,
              availability_status: profile.availabilityStatus,
              profile_visibility: profile.profileVisibility,
              timezone: profile.timezone,
            },
          },
        });
        if (authError) throw authError;
        if (!data.session) {
          setNotice("Registration received. Check your email to verify the account, then sign in.");
          setMode("login");
          return;
        }
        onAuthenticated(data.session);
      } else {
        const { data, error: authError } = await supabase.auth.signInWithPassword({
          email: credentials.email.trim(),
          password: credentials.password,
        });
        if (authError) throw authError;
        onAuthenticated(data.session);
      }
    } catch (caught) {
      setError(caught.message || "Authentication failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="auth-section" id="access">
      <div className="section-id light">02 / IDENTITY CLEARANCE</div>
      <motion.div
        className="auth-panel"
        initial={{ opacity: 0, y: 50 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true, amount: .2 }}
        transition={{ duration: .85 }}
      >
        <div className="auth-intro">
          <p>Secure access</p>
          <h2>{mode === "register" ? "Join your workforce." : "Return to command."}</h2>
          <span>
            Sign in to describe a project, review a recommended collaborator, and manage invitations.
          </span>
        </div>

        <form className="auth-form" onSubmit={submit}>
            {!isSupabaseConfigured && (
              <div className="auth-config">
                <strong>Supabase configuration required</strong>
                <p>Add VITE_SUPABASE_URL and VITE_SUPABASE_PUBLISHABLE_KEY to frontend/.env.local.</p>
              </div>
            )}
            <div className="auth-tabs" role="tablist">
              <button type="button" className={mode === "register" ? "active" : ""} onClick={() => setMode("register")}>Register</button>
              <button type="button" className={mode === "login" ? "active" : ""} onClick={() => setMode("login")}>Sign in</button>
            </div>

            <div className="field-row">
              <label>Email<input type="email" autoComplete="email" required value={credentials.email} onChange={updateCredentials("email")} /></label>
              <label>Password<input type="password" autoComplete={mode === "register" ? "new-password" : "current-password"} minLength="8" required value={credentials.password} onChange={updateCredentials("password")} /></label>
            </div>

            {mode === "register" && (
              <motion.div className="profile-fields" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
                <div className="field-row">
                  <label>Display name<input required maxLength="120" value={profile.displayName} onChange={updateProfile("displayName")} /></label>
                  <label>Organization <small>optional</small><input maxLength="180" value={profile.institution} onChange={updateProfile("institution")} /></label>
                </div>
                <label>Short bio <small>optional</small><textarea rows="3" maxLength="1000" value={profile.bio} onChange={updateProfile("bio")} /></label>
                <label>Capabilities <small>comma separated</small><input placeholder="React, interface design, machine learning" value={profile.skills} onChange={updateProfile("skills")} /></label>
                <label>Interests <small>comma separated</small><input placeholder="Hackathons, climate tech, open source" value={profile.interests} onChange={updateProfile("interests")} /></label>
                <div className="field-row">
                  <label>Availability<select value={profile.availabilityStatus} onChange={updateProfile("availabilityStatus")}><option value="AVAILABLE">Available</option><option value="LIMITED">Limited</option><option value="UNAVAILABLE">Unavailable</option></select></label>
                  <label>Profile visibility<select value={profile.profileVisibility} onChange={updateProfile("profileVisibility")}><option value="MEMBERS">Members</option><option value="PUBLIC">Public</option><option value="PRIVATE">Private</option></select></label>
                </div>
              </motion.div>
            )}

            {notice && <p className="auth-notice">{notice}</p>}
            {error && <p className="auth-error">{error}</p>}
            <button className="auth-submit" type="submit" disabled={busy || !isSupabaseConfigured}>
              {busy ? "Verifying…" : mode === "register" ? "Create account" : "Authenticate"}
              <span>→</span>
            </button>
          </form>
      </motion.div>
    </section>
  );
}
