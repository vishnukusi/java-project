/* SentinelCV API client. Reads the __DATA__ JSON islands that the existing Thymeleaf views embed;
   the only true JSON endpoints in the backend are the two /audits/{id}/status routes. */
window.SCV=(()=>{
  const island=h=>{const m=h.match(/<script[^>]*id="__DATA__"[^>]*>([\s\S]*?)<\/script>/i);if(!m)return undefined;try{return JSON.parse(m[1].trim())}catch{return undefined}};
  const page=async p=>{const r=await fetch(p,{headers:{Accept:'text/html'}});if(!r.ok)throw new Error(`Request failed (${r.status}).`);const d=island(await r.text());if(d===undefined)throw new Error('Unexpected response from the server.');return d};
  const json=async p=>{const r=await fetch(p,{headers:{Accept:'application/json'}});if(!r.ok)throw new Error(`Request failed (${r.status}).`);return r.json()};
  /* Form POSTs answer with a redirect; the final URL says where the backend sent us. */
  const post=(p,body,onProgress)=>new Promise((ok,no)=>{const x=new XMLHttpRequest();x.open('POST',p);
    if(onProgress&&x.upload)x.upload.onprogress=e=>e.lengthComputable&&onProgress(e.loaded/e.total);
    x.onerror=()=>no(new Error('Cannot reach the SentinelCV server.'));
    x.onload=()=>{if(x.status>=400)return no(new Error(`The server rejected the request (${x.status}).`));
      ok({path:new URL(x.responseURL||p,location.href).pathname.replace(/;jsessionid=[^/]*/i,''),data:island(x.responseText)})};
    x.send(body||null)});
  const parseTime=v=>{if(v==null)return null;if(Array.isArray(v)){const[y,mo,d,h=0,mi=0,s=0,n=0]=v;return new Date(y,mo-1,d,h,mi,s,Math.floor(n/1e6))}const d=new Date(v);return isNaN(d)?null:d};
  const fmtTime=v=>{const d=parseTime(v);return d?d.toLocaleString(undefined,{day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit'}):'—'};
  const num=v=>v==null?'—':Number(v).toLocaleString('en-US');
  const el=(t,a={},...k)=>{const e=document.createElement(t);for(const[n,v]of Object.entries(a)){if(v==null)continue;n==='class'?e.className=v:e.setAttribute(n,v)}
    for(const c of k.flat())if(c!=null)e.append(c.nodeType?c:document.createTextNode(c));return e};
  const badge=(cls,g,t)=>el('span',{class:'badge '+cls},el('i',{'aria-hidden':'true'},g),t);
  const D={ACCEPT:['b-ok','✓'],REVIEW:['b-warn','!'],QUARANTINE:['b-crit','✕']};
  const C={PASSED:['b-ok','✓','PASSED'],FAILED:['b-crit','✕','FAILED'],UNAVAILABLE:['b-na','⊘','UNAVAILABLE'],NOT_EXECUTED:['b-na','○','NOT RUN']};
  const decision=d=>D[d]?badge(D[d][0],D[d][1],d):badge('b-na','⊘',d||'Not assessed');
  const check=s=>C[s]?badge(...C[s]):badge('b-na','○',s||'Not reported');
  const banner=(host,kind,msg)=>{host.replaceChildren();if(msg)host.append(el('div',{class:'banner '+(kind==='err'?'err':'okb'),role:kind==='err'?'alert':'status'},msg))};
  return{page,json,post,parseTime,fmtTime,num,el,badge,decision,check,banner}})();

