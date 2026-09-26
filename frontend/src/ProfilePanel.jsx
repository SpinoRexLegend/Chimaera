import { useEffect, useState } from "react";
const API = import.meta.env.VITE_API_URL || "/api";

export function ResumeButton({ session, path, label = "View resume (PDF)" }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function open() {
    setBusy(true); setError("");
    try {
      const response = await fetch(`${API}${path}`, { headers: { Authorization: `Bearer ${session.access_token}` } });
      if (!response.ok) throw new Error(response.status === 404 ? "No resume available." : "Unable to retrieve resume. Please retry.");
      const url = URL.createObjectURL(await response.blob());
      const link = document.createElement("a");
      link.href = url; link.download = "resume.pdf"; link.click();
      setTimeout(() => URL.revokeObjectURL(url), 60000);
    } catch (e) { setError(e.message); }
    finally { setBusy(false); }
  }
  return <span className="resume-action"><button type="button" onClick={open} disabled={busy}>{busy ? "Loading…" : label}</button>{error && <small role="alert">{error}</small>}</span>;
}

export default function ProfilePanel({ session }) {
  const [profile, setProfile] = useState(null);
  const [resume, setResume] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  async function api(path, options = {}) {
    const response = await fetch(`${API}${path}`, { ...options, headers: { Authorization: `Bearer ${session.access_token}`, ...options.headers } });
    if (!response.ok) throw new Error(`Unable to save or load profile (${response.status}). Please retry.`);
    return response.status === 204 ? null : response.json();
  }
  useEffect(() => {
    let cancelled = false;
    Promise.all([api("/auth/profile"), api("/profile/resume/info")]).then(([p, r]) => {
      if (!cancelled) { setProfile({ ...p, skills: p.skills.join(", "), interests: p.interests.join(", ") }); setResume(r); }
    }).catch(e => { if (!cancelled) setError(e.message); });
    return () => { cancelled = true; };
  }, [session.access_token]);
  const change = key => event => setProfile(p => ({ ...p, [key]: event.target.value }));
  async function save(event) {
    event.preventDefault(); setBusy(true); setError(""); setNotice("");
    try {
      const split = value => value.split(",").map(s => s.trim()).filter(Boolean);
      await api("/auth/profile", { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ ...profile, skills: split(profile.skills), interests: split(profile.interests) }) });
      setNotice("Profile saved. Your updated skills will be used for future matches.");
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  async function upload(event) {
    const file = event.target.files?.[0]; event.target.value = "";
    if (!file) return;
    setError(""); setNotice("");
    if (!file.name.toLowerCase().endsWith(".pdf") || file.size > 5 * 1024 * 1024) { setError("Choose a PDF no larger than 5 MB."); return; }
    setBusy(true);
    try {
      const data = new FormData(); data.append("file", file);
      const parsed = await api("/profile/resume", { method: "PUT", body: data });
      const saved = await api("/auth/profile");
      setResume(parsed);
      setProfile({ ...saved, skills: saved.skills.join(", "), interests: saved.interests.join(", ") });
      setNotice(parsed.parsingMessage || "Resume uploaded and your profile was updated.");
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  async function remove() {
    if (!window.confirm("Remove your uploaded resume? You can upload it again later.")) return;
    setBusy(true); setError(""); setNotice("");
    try { await api("/profile/resume", { method: "DELETE" }); setResume({ available: false }); setNotice("Resume removed."); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  return <div className="profile-panel" role="tabpanel">
    <div className="profile-heading"><div className="profile-avatar" aria-hidden="true">{profile?.displayName?.slice(0, 1).toUpperCase() || "C"}</div><div><span className="profile-eyebrow">Member profile</span><h3>{profile?.displayName || "Your profile"}</h3><p>Your skills. Your next collaboration.</p></div></div>
    {error && <p role="alert" className="auth-error">{error}</p>}
    {notice && <p role="status" className="auth-notice">{notice}</p>}
    {!profile ? <p>{error ? "Reopen the Profile tab to retry." : "Loading profile…"}</p> : <>
      <div className="profile-layout"><form onSubmit={save} className="profile-form">
        <div className="wide profile-section-title"><h4>Personal details</h4><p>Tell collaborators a little about yourself.</p></div>
        <label>Email<input value={profile.email || session.user.email} readOnly /></label>
        <label>Display name<input required maxLength={120} value={profile.displayName} onChange={change("displayName")} /></label>
        <label>Organization<input maxLength={180} value={profile.institution || ""} onChange={change("institution")} /></label>
        <label>Time zone<input required maxLength={64} value={profile.timezone} onChange={change("timezone")} /></label>
        <label className="wide">Bio<textarea maxLength={1000} rows={3} value={profile.bio || ""} onChange={change("bio")} /></label>
        <div className="wide profile-section-title"><h4>Capabilities & preferences</h4><p>Your saved skills help people find you.</p></div>
        <label className="wide">Skills (comma separated)<input value={profile.skills} onChange={change("skills")} placeholder="React, Python, product design" /></label>
        <label className="wide">Interests (comma separated)<input value={profile.interests} onChange={change("interests")} /></label>
        <label>Availability<select value={profile.availabilityStatus} onChange={change("availabilityStatus")}><option>AVAILABLE</option><option>LIMITED</option><option>UNAVAILABLE</option></select></label>
        <label>Profile visibility<select value={profile.profileVisibility} onChange={change("profileVisibility")}><option>MEMBERS</option><option>PUBLIC</option><option>PRIVATE</option></select></label>
        <div className="wide profile-save"><span>Changes take effect after saving.</span><button disabled={busy} type="submit">{busy ? "Working…" : "Save changes"}</button></div>
      </form>
      <section className="resume-section"><h4>Your resume</h4>
        <div className="resume-document" aria-hidden="true">PDF</div><p>Upload a text-based resume to update your organization and skills.</p><p className="resume-help">PDF · up to 5 MB. A detected Skills section replaces stale profile skills and remains editable. Scanned documents require OCR.</p>
        <label>{resume?.available ? "Replace resume" : "Upload resume"}<input type="file" accept=".pdf,application/pdf" disabled={busy} onChange={upload} /></label>
        {resume?.available && <div><p>Uploaded {new Date(resume.updatedAt).toLocaleString()}</p><ResumeButton session={session} path="/profile/resume" label="Download my resume" /> <button type="button" disabled={busy} onClick={remove}>Remove resume</button></div>}
      </section></div>
    </>}
  </div>;
}
