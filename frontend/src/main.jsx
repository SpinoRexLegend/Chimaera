import { StrictMode, useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import { MotionConfig, motion, useScroll, useSpring, useTransform } from "framer-motion";
import AuthPortal from "./AuthPortal";
import ProfilePanel, { ResumeButton } from "./ProfilePanel";
import FaqPage from "./FaqPage";
import InboxRequest from "./InboxRequest";
import SentRequest from "./SentRequest";
import ProposalChat from "./ProposalChat";
import { WorkforceTasks } from "./Workforce";
import { supabase } from "./supabase";
import "./styles.css";

const API = import.meta.env.VITE_API_URL || "/api";

function Arrow() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6" /></svg>;
}

function Emblem() {
  return <span className="emblem" aria-hidden="true"><i /><i /><i /><b /></span>;
}

function ThemeToggle({ theme, onToggle }) {
  const next = theme === "dark" ? "light" : "dark";
  return <button className="theme-toggle" type="button" onClick={onToggle} aria-label={`Switch to ${next} theme`} title={`Switch to ${next} theme`}><span aria-hidden="true">{theme === "dark" ? "☼" : "◐"}</span>{next}</button>;
}

function Hero({ onBegin, signedIn, onSignOut, theme, onToggleTheme }) {
  const ref = useRef(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start start", "end start"] });
  const smooth = useSpring(scrollYProgress, { stiffness: 48, damping: 18, mass: .35 });
  const geometryY = useTransform(smooth, [0, 1], ["-2%", "14%"]);
  const geometryScale = useTransform(smooth, [0, 1], [1.02, 1.1]);
  const copyY = useTransform(smooth, [0, 1], [0, 180]);
  const copyOpacity = useTransform(smooth, [0, .68], [1, 0]);

  return (
    <header className="hero" ref={ref} id="top">
      <motion.div
        className="hero-geometry"
        aria-hidden="true"
        style={{ y: geometryY, scale: geometryScale }}
      />
      <div className="hero-shade" />
      <nav className="nav">
        <a className="brand" href="#top"><Emblem /><span>CHIMAERA</span></a>
        <div className="nav-center">Intelligent workforce orchestration</div>
        <div className="nav-actions"><a href="/faq">FAQ</a><ThemeToggle theme={theme} onToggle={onToggleTheme} /><button type="button" onClick={onBegin}>{signedIn ? "Open workspace" : "Log in / Register"} <Arrow /></button>{signedIn && <button type="button" onClick={onSignOut}>Sign out</button>}</div>
      </nav>
      <motion.div className="hero-copy" style={{ y: copyY, opacity: copyOpacity }}>
        <div className="app-title">CHIMAERA</div>
        <p className="overline"><span /> Assembly intelligence online</p>
        <h1>Different minds.<br /><em>One creation.</em></h1>
        <p className="hero-lede">
          Describe your project. CHIMAERA maps the skills it needs, recommends the strongest
          collaborator, and sends an invitation only after your approval.
        </p>
        <button className="hero-action" type="button" onClick={onBegin}>
          Plan a project <Arrow />
        </button>
      </motion.div>
      <div className="hero-coordinate">SYS / CHM-01<br />SCROLL TO DESCEND</div>
    </header>
  );
}

const phases = [
  ["01", "AI agent", "Natural-language project goals are converted into focused, editable skill requirements."],
  ["02", "Resume parsing", "Text-based resumes extract organization and real Skills-section values directly into editable member profiles."],
  ["03", "ML matching", "Eligible collaborators are ranked from saved skills, experience, availability, and live profile data."],
  ["04", "Human approval", "The agent drafts the best invitation, but nothing is sent until the requester authorizes it."],
  ["05", "Request control", "Recipients can review, accept, decline, or remove invitations while senders track every response."],
  ["06", "Private chat", "An accepted collaboration opens a private conversation between the matched teammates."],
  ["07", "Secure profiles", "Supabase identity, protected resumes, visibility settings, and candidate checks preserve access boundaries."],
  ["08", "Parallel service", "Bounded concurrent AI work keeps independent matching requests responsive under load."],
];

function Protocol() {
  const ref = useRef(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start end", "end start"] });
  const smooth = useSpring(scrollYProgress, { stiffness: 42, damping: 20, mass: .45 });
  const titleX = useTransform(smooth, [0, .55], [-90, 0]);
  const lineScale = useTransform(smooth, [.12, .75], [0, 1]);

  return (
    <section className="protocol" ref={ref}>
      <div className="section-id">01 / ROUTING PROTOCOL</div>
      <motion.div className="protocol-title" style={{ x: titleX }}>
        <p>Production-minded intelligence for small teams.</p>
        <h2>Every capability.<br /><em>One orchestration layer.</em></h2>
      </motion.div>
      <div className="phase-rail">
        <motion.i style={{ scaleX: lineScale }} />
        {phases.map(([number, title, copy], index) => (
          <motion.article
            key={title}
            initial={{ opacity: 0, y: 70, rotateX: 12 }}
            whileInView={{ opacity: 1, y: 0, rotateX: 0 }}
            viewport={{ once: true, amount: .55 }}
            transition={{ duration: .8, delay: index * .09, ease: [0.22, 1, 0.36, 1] }}
          >
            <span>{number}</span><h3>{title}</h3><p>{copy}</p>
          </motion.article>
        ))}
      </div>
    </section>
  );
}

function AgentWorkspace({ session, onSignOut, theme, onToggleTheme }) {
  const ref = useRef(null);
  const inputRef = useRef(null);
  const [prompt, setPrompt] = useState("");
  const [requiredSkills, setRequiredSkills] = useState("");
  const [busy, setBusy] = useState(false);
  const [proposal, setProposal] = useState(null);
  const [activeView, setActiveView] = useState("request");
  const [inbox, setInbox] = useState([]);
  const [inboxState, setInboxState] = useState("idle");
  const [sent, setSent] = useState([]);
  const [sentState, setSentState] = useState("idle");
  const [chat, setChat] = useState(null);
  const [messages, setMessages] = useState([
    { role: "agent", text: "Describe your project and deliverables. I will identify the required skills, search member profiles, and recommend the strongest collaborator for your approval." },
  ]);
  useEffect(() => {
    if (activeView !== "inbox" && activeView !== "sent") return undefined;
    let cancelled = false;
    const showingSent = activeView === "sent";
    (showingSent ? setSentState : setInboxState)("loading");
    fetch(`${API}/inbox/${showingSent ? "sent" : "proposals"}`, {
      headers: { Authorization: `Bearer ${session.access_token}` },
    })
      .then(async (response) => {
        if (!response.ok) throw new Error((await response.text()) || "Unable to load inbox");
        return response.json();
      })
      .then((items) => {
        if (!cancelled) {
          (showingSent ? setSent : setInbox)(items);
          (showingSent ? setSentState : setInboxState)("ready");
        }
      })
      .catch(() => {
        if (!cancelled) (showingSent ? setSentState : setInboxState)("error");
      });
    return () => { cancelled = true; };
  }, [activeView, session.access_token]);

  const request = async (path, options = {}) => {
    const response = await fetch(`${API}${path}`, {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${session.access_token}`,
      },
      ...options,
    });
    if (!response.ok) throw new Error((await response.text()) || `Request failed: ${response.status}`);
    return response.json();
  };

  async function runAgent(event) {
    event.preventDefault();
    const goal = prompt.trim();
    if (!goal || busy) return;
    setPrompt("");
    setBusy(true);
    setProposal(null);
    setMessages((current) => [...current, { role: "user", text: goal }, { role: "agent", text: "Interpreting mission parameters and building the capability map…", pending: true }]);

    try {
      const quest = await request("/quests", {
        method: "POST",
        body: JSON.stringify({
          title: goal.slice(0, 110),
          publicSummary: goal.slice(0, 600),
          privateDescription: goal,
          deadline: new Date(Date.now() + 90 * 86400000).toISOString().slice(0, 10),
          maxMembers: 6,
          desiredSkills: requiredSkills.split(",").map(s => s.trim()).filter(Boolean),
        }),
      });
      const analysis = await request(`/quests/${quest.id}/analyse`, { method: "POST" });
      const matches = await request(`/quests/${quest.id}/match`, { method: "POST" });
      if (!matches.length) {
        setMessages(current => [...current.filter(message => !message.pending), { role: "agent", text: "No eligible collaborator currently matches the requested skills. Refine the skills or try again after more members update their profiles." }]);
      } else {
        const selected = matches[0];
        const draft = await request(`/quests/${quest.id}/proposals/draft`, { method: "POST", body: JSON.stringify({ candidateId: selected.id, candidateName: selected.name }) });
        setProposal(draft);
        setMessages(current => [...current.filter(message => !message.pending), {
          role: "agent", text: `I recommend ${selected.name}. Review the profile evidence below, then authorize the invitation if the match looks right.`,
          matches, approval: true, questId: quest.id,
        }]);
      }
    } catch (error) {
      setMessages((current) => [...current.filter((message) => !message.pending), { role: "agent", text: `Unable to complete the route: ${error.message}`, error: true }]);
    } finally {
      setBusy(false);
    }
  }

  async function approveProposal() {
    if (!proposal || busy) return;
    setBusy(true);
    try {
      const sent = await request(`/proposals/${proposal.id}/send`, {
        method: "POST",
        body: JSON.stringify({ approved: true }),
      });
      setProposal(sent);
      setMessages((current) => [...current, { role: "agent", text: "Authorization confirmed. The collaboration proposal has been transmitted.", success: true }]);
    } catch (error) {
      setMessages((current) => [...current, { role: "agent", text: error.message, error: true }]);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="agent-section" id="agent" ref={ref}>
      <div className="agent-geometry" aria-hidden="true" />
      <div className="agent-shade" />
      <div className="section-id light">02 / ASSEMBLY AGENT</div>
      <div className="agent-shell">
        <div className="terminal-head">
          <div><Emblem /><span>CHIMAERA AGENT</span></div>
          <div className="session-tools">
            <p><i /> {session.user.email}</p>
            <ThemeToggle theme={theme} onToggle={onToggleTheme} />
            <button type="button" onClick={onSignOut}>Sign out</button>
          </div>
        </div>
        <div className="workspace-tabs" role="tablist" aria-label="Collaboration workspace">
          <button type="button" role="tab" aria-selected={activeView === "request"} onClick={() => setActiveView("request")}>
            <span>01</span> Plan project
          </button>
          <button type="button" role="tab" aria-selected={activeView === "inbox"} onClick={() => setActiveView("inbox")}>
            <span>02</span> Inbox <b>{inbox.length}</b>
          </button>
          <button type="button" role="tab" aria-selected={activeView === "sent"} onClick={() => setActiveView("sent")}>
            <span>03</span> Sent <b>{sent.length}</b>
          </button>
          <button type="button" role="tab" aria-selected={activeView === "profile"} onClick={() => setActiveView("profile")}><span>04</span> My profile</button>
        </div>

        {activeView === "profile" ? <ProfilePanel session={session} /> : activeView === "request" ? <>
        <div className="conversation" aria-live="polite">
          {messages.map((message, index) => (
            <motion.div
              className={`message ${message.role} ${message.error ? "error" : ""}`}
              key={index}
              initial={{ opacity: 0, y: 18 }}
              animate={{ opacity: 1, y: 0 }}
            >
              <span>{message.role === "agent" ? "CHIMAERA" : "YOU"}</span>
              <p>{message.text}</p>
              {message.capabilities?.length > 0 && <div className="capabilities">{message.capabilities.map((item) => <b key={item}>{item}</b>)}</div>}
              {message.matches?.length > 0 && (
                <div className="match-strip">
                  {message.matches.slice(0, 3).map((match) => (
                    <article key={match.id}>
                      <i>{match.name.slice(0, 1)}</i><div><strong>{match.name}</strong><small>Skill similarity</small><small>{match.reasons?.[0]}</small><ResumeButton session={session} path={`/quests/${message.questId}/candidates/${match.id}/resume`} /></div><b>{Math.round(match.matchScore * 100)}%</b>
                    </article>
                  ))}
                </div>
              )}
              {message.approval && proposal?.status !== "SENT" && (
                <div><p>Sending shares your profile and uploaded resume with the recipient.</p><button className="approve" type="button" onClick={approveProposal} disabled={busy}>Authorize proposal <Arrow /></button></div>
              )}
            </motion.div>
          ))}
          {busy && <div className="thinking"><i /><i /><i /><span>Agent working</span></div>}
        </div>
        <form className="command-box" onSubmit={runAgent}>
          <label htmlFor="required-skills">Skills you need (comma separated, optional)</label>
          <input id="required-skills" value={requiredSkills} onChange={event => setRequiredSkills(event.target.value)} placeholder="React, Python, product design" />
          <label htmlFor="mission">Describe your project requirements</label>
          <textarea
            ref={inputRef}
            id="mission"
            rows="3"
            value={prompt}
            onChange={(event) => setPrompt(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) runAgent(event);
            }}
            placeholder="Example: I need a collaborator who can build a React dashboard backed by a Python API and SQL reporting."
          />
          <div>
            <span>Enter to match · You approve every invitation</span>
            <button type="submit" disabled={busy || !prompt.trim()} aria-label="Send mission to CHIMAERA"><Arrow /></button>
          </div>
        </form>
        </> : (
          <div className="inbox-view" role="tabpanel">
            <div className={`collaboration-layout ${chat ? "chat-open" : ""}`}>
              <div className="collaboration-stack">
                <div className="inbox-heading">
                  <div><p>{activeView === "inbox" ? "Incoming collaboration requests" : "Outgoing collaboration requests"}</p><h3>{activeView === "inbox" ? "Your inbox" : "Requests you sent"}</h3></div>
                  <button type="button" onClick={() => { setActiveView("request"); setChat(null); setTimeout(() => inputRef.current?.focus(), 0); }}>Create a request <Arrow /></button>
                </div>
                {activeView === "inbox" && <WorkforceTasks session={session}/>} 
                {activeView === "inbox" ? <>
                  {inboxState === "loading" && <div className="inbox-empty">Decrypting incoming transmissions…</div>}
                  {inboxState === "error" && <div className="inbox-empty error">The inbox could not be loaded. Try signing in again.</div>}
                  {inboxState === "ready" && inbox.length === 0 && <div className="inbox-empty"><Emblem /><strong>No incoming requests</strong><p>Collaboration invitations addressed to your verified account will appear here.</p></div>}
                  {inbox.length > 0 && <div className="inbox-list">{inbox.map(item => <motion.div key={item.id} initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }}><InboxRequest item={item} session={session} onUpdated={updated => setInbox(current => current.map(p => p.id === updated.id ? updated : p))} onAccepted={accepted => setChat({...accepted, chatPartner: accepted.senderName})} onDeleted={id => { setInbox(current => current.filter(p => p.id !== id)); setChat(current => current?.id === id ? null : current); }} /></motion.div>)}</div>}
                </> : <>
                  {sentState === "loading" && <div className="inbox-empty">Loading sent requests…</div>}
                  {sentState === "error" && <div className="inbox-empty error">Sent requests could not be loaded.</div>}
                  {sentState === "ready" && sent.length === 0 && <div className="inbox-empty"><Emblem /><strong>No sent requests</strong><p>Requests you authorize will appear here with their latest response.</p></div>}
                  {sent.length > 0 && <div className="inbox-list">{sent.map(item => <SentRequest key={item.id} item={item} onChat={proposal => setChat({...proposal, chatPartner: proposal.receiverName})} />)}</div>}
                </>}
              </div>
              {chat && <ProposalChat proposal={chat} session={session} onClose={() => setChat(null)} />}
            </div>
          </div>
        )}
      </div>
    </section>
  );
}

function App({ theme, onToggleTheme }) {
  const [session, setSession] = useState(null);
  const [authReady, setAuthReady] = useState(false);
  const [profileError, setProfileError] = useState("");

  useEffect(() => {
    if (!supabase) {
      setAuthReady(true);
      return undefined;
    }
    let active = true;
    let revision = 0;
    let syncInFlight = null;
    let syncedSubject = null;
    async function prepare(nextSession) {
      if (!active) return;
      const current = ++revision;
      setAuthReady(false);
      setProfileError("");
      if (nextSession) {
        if (syncedSubject === nextSession.user.id) {
          if (active && current === revision) { setSession(nextSession); setAuthReady(true); }
          return;
        }
        const m = nextSession.user.user_metadata || {};
        try {
          if (!syncInFlight) syncInFlight = fetch(`${API}/auth/sync`, { method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${nextSession.access_token}` }, body: JSON.stringify({
              displayName: m.display_name || nextSession.user.email?.split("@")[0] || "Member", institution: m.institution || "", bio: m.bio || "",
              skills: m.skills || [], interests: m.interests || [], availabilityStatus: m.availability_status || "AVAILABLE", profileVisibility: m.profile_visibility || "MEMBERS", timezone: m.timezone || "UTC"
            }) });
          const response = await syncInFlight;
          if (!response.ok) throw new Error("Your profile could not be saved. Refresh to retry before creating requests.");
          syncedSubject = nextSession.user.id;
        } catch (error) {
          if (active && current === revision) { setProfileError(error.message); setSession(null); setAuthReady(true); }
          return;
        } finally {
          syncInFlight = null;
        }
      } else {
        syncedSubject = null;
      }
      if (active && current === revision) { setSession(nextSession); setAuthReady(true); }
    }
    supabase.auth.getSession().then(({ data }) => { if (!revision) prepare(data.session); }).catch(() => { if (active) { setAuthReady(true); setProfileError("Unable to restore your session. Please sign in again."); } });
    const { data } = supabase.auth.onAuthStateChange((_event, nextSession) => {
      if (_event !== "TOKEN_REFRESHED") prepare(nextSession);
      else setSession(current => current ? nextSession : null);
    });
    return () => { active = false; data.subscription.unsubscribe(); };
  }, []);

  const begin = () => document.querySelector(session ? "#agent" : "#access")?.scrollIntoView({ behavior: "smooth" });
  const signOut = async () => {
    const result = await supabase?.auth.signOut({ scope: "local" });
    if (result?.error) { setProfileError("Sign-out failed. Please retry."); return; }
    setSession(null);
    setProfileError("");
    setTimeout(() => document.querySelector("#access")?.scrollIntoView({ behavior: "smooth" }), 0);
  };
  return (
    <MotionConfig reducedMotion="user" transition={{ ease: [0.22, 1, 0.36, 1] }}>
      <main>
        <Hero onBegin={begin} signedIn={Boolean(session)} onSignOut={signOut} theme={theme} onToggleTheme={onToggleTheme} />
        <Protocol />
        {profileError && <p className="auth-error" role="alert">{profileError}</p>}
        {!authReady && <p className="auth-notice" role="status">Preparing your workspace…</p>}
        {(session && authReady
          ? <AgentWorkspace session={session} onSignOut={signOut} theme={theme} onToggleTheme={onToggleTheme} />
          : <AuthPortal onAuthenticated={() => {}} />)}
        <footer><a href="#top"><Emblem /> CHIMAERA</a><p>Created by <strong>Aritra Maji</strong> · Intelligent workforce orchestration</p><div className="footer-links"><a href="/faq">Edge-case FAQ</a><a href="#top">Return to command ↑</a></div></footer>
      </main>
    </MotionConfig>
  );
}

const route = window.location.pathname.replace(/\/+$/, "") || "/";
if (route === "/faq") document.title = "Edge-case FAQ — CHIMAERA";
let appRoot = import.meta.hot?.data.appRoot;
if (!appRoot) appRoot = createRoot(document.getElementById("root"));
if (import.meta.hot) import.meta.hot.data.appRoot = appRoot;
function Root() {
  const [theme, setTheme] = useState(() => localStorage.getItem("chimaera-theme") === "light" ? "light" : "dark");
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.documentElement.style.colorScheme = theme;
    localStorage.setItem("chimaera-theme", theme);
  }, [theme]);
  const toggleTheme = () => setTheme(current => current === "dark" ? "light" : "dark");
  return route === "/faq" ? <FaqPage theme={theme} onToggleTheme={toggleTheme} /> : <App theme={theme} onToggleTheme={toggleTheme} />;
}
appRoot.render(<StrictMode><Root /></StrictMode>);
