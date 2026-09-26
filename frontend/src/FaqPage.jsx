const groups = [
  { title: "Collaborator matching", items: [
    ["How do I start?", "Describe the project and optionally provide the skills you need. CHIMAERA extracts requirements, searches saved member profiles, and presents the strongest eligible collaborator for your approval."],
    ["How are collaborators selected?", "The model computes TF-IDF similarity from requested skills and skills saved in member profiles. This is an explainable recommendation, not a prediction of performance."],
    ["What if nobody matches?", "Refine overly narrow skill requirements or try again after more members update their profiles. CHIMAERA does not invent a candidate."],
    ["Can a resume update my profile?", "Yes. Uploading a text-based PDF reads its Skills section and detected organization, then replaces stale resume-derived profile values with the new document’s values. The fields remain editable. Scanned image-only PDFs require OCR and will not be auto-filled."],
    ["Can I describe skills in ordinary language?", "Yes. The extractor compares your description with the employee skill catalog and combines that result with any skills you enter explicitly. Clear, specific wording produces better recommendations."],
  ]},
  { title: "Accounts and profiles", items: [
    ["What if my verification email does not arrive?", "Check spam and confirm that the address was typed correctly. Supabase may rate-limit repeated requests, so wait briefly before trying again. Your CHIMAERA profile is created only after a verified session reaches the backend."],
    ["What happens if my login expires?", "Protected requests return an authorization error. Sign in again to obtain a fresh session; saved profiles, requests, inbox items, and resumes remain in MySQL."],
    ["Can I hide myself from matching?", "Yes. Set availability to Unavailable or visibility to Private in My profile. Either setting removes you from the eligible candidate pool."],
    ["Why are my profile edits different from registration details?", "Registration metadata initializes a profile once. Later edits in My profile are the authoritative values and are preserved on future logins."],
  ]},
  { title: "Matching and requests", items: [
    ["What if no candidate is found?", "The request stays unsent. Add precise required skills, broaden overly narrow requirements, or retry after more members register. CHIMAERA does not invent candidates or send a request without an eligible match."],
    ["What does the match percentage mean?", "It is TF-IDF skill similarity across the current eligible candidate pool. It is not a probability of success, a personality score, or a guarantee that the person will accept."],
    ["What if several people have the same skills?", "The model ranks relevance using the live pool. Equal scores use a stable identifier order, so the result is repeatable, but human review remains required before sending."],
    ["Can the agent contact someone automatically?", "No. It can prepare a proposal, but the request owner must explicitly authorize transmission."],
    ["What if a candidate becomes private or unavailable after being selected?", "Eligibility is checked again before protected candidate data is returned. The owner should rerun matching rather than relying on a stale selection."],
    ["Does the model read resumes when ranking?", "Resume text updates organization and profile skills during upload. Matching then uses those saved profile skills; the uploaded document itself is not sent to the ranking model."],
  ]},
  { title: "Resumes, privacy, and safety", items: [
    ["Who can view my resume?", "You can download your own resume. Request owners can download resumes of currently eligible matches. When you authorize an invitation, its recipient can also download your resume while reviewing it. There is no public resume URL."],
    ["What if a candidate has no resume?", "The candidate can still be matched from their declared skills. The resume action reports that no file is available."],
    ["Which resume files are accepted?", "PDF files up to 5 MB. CHIMAERA checks the size and PDF signature, but this local version does not perform malware scanning. Avoid including unnecessary sensitive information."],
    ["What happens when I replace or remove a resume?", "Replacing overwrites the stored PDF for your profile. Removing deletes it from the active resume table, and future candidate download attempts return unavailable."],
    ["Can another user guess a resume URL?", "Knowing an identifier is insufficient. The backend requires a valid login and verifies either ownership, current candidate eligibility for your request, or that you are the recipient of a sent invitation."],
    ["How do I respond to an invitation?", "Open Inbox and select Review request. You can read its summary, see requested skills, download the sender’s resume if available, and accept or decline. Accepting opens a private CHIMAERA chat with the sender; declining only records the response."],
    ["Where can I see requests I sent?", "Open the Sent tab in your workspace. It shows each recipient and the latest status. Once a request is accepted, either participant can reopen its private chat."],
    ["What does deleting an inbox offer do?", "It removes the offer from your inbox only. It does not erase the sender’s sent-request history or any accepted conversation."],
  ]},
  { title: "Failures and unusual conditions", items: [
    ["What if the AI service is offline?", "Analysis may fall back to owner-provided skills, but matching fails safely instead of returning fabricated candidates. Retry when the service is healthy."],
    ["What if the database or backend is offline?", "Profile saves, matching, proposals, inbox access, and resumes will fail without changing completed data. Refresh only after the affected service is restored."],
    ["What if two requests are submitted quickly?", "Each quest receives its own identifier and proposal state. Wait for one run to finish before authorizing another so you can review the intended recipient."],
    ["Why are animations limited on my device?", "CHIMAERA respects the operating system's reduced-motion preference. Core navigation and features continue working with transitions minimized."],
    ["Does an inbox item mean an email was sent?", "No. The inbox contains internal CHIMAERA collaboration proposals. External email delivery is not part of the current system."],
  ]},
];

function Mark() {
  return <span className="faq-mark" aria-hidden="true"><i /><i /><i /><b /></span>;
}

export default function FaqPage({ theme, onToggleTheme }) {
  return <main className="faq-page" id="top">
    <nav className="faq-nav"><a href="/"><Mark /><span>CHIMAERA</span></a><div><button className="theme-toggle" type="button" onClick={onToggleTheme} aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} theme`}>{theme === "dark" ? "☼ Light" : "◐ Dark"}</button><a href="/">Return to command →</a></div></nav>
    <header className="faq-hero">
      <p>Operational reference / edge cases</p>
      <h1>Questions that matter<br /><em>when things get complicated.</em></h1>
      <p>Clear behavior for unusual conditions, privacy boundaries, matching limitations, and service failures.</p>
    </header>
    <section className="faq-content" aria-label="Frequently asked questions">
      {groups.map((group, groupIndex) => <section className="faq-group" key={group.title}>
        <div><span>0{groupIndex + 1}</span><h2>{group.title}</h2></div>
        <div>{group.items.map(([question, answer]) => <details key={question}>
          <summary>{question}<span aria-hidden="true">+</span></summary><p>{answer}</p>
        </details>)}</div>
      </section>)}
    </section>
    <footer className="faq-footer"><a href="/"><Mark /> CHIMAERA</a><p>Created by <strong>Aritra Maji</strong> · Human-authorized intelligence</p><a href="#top">Back to top ↑</a></footer>
  </main>;
}
