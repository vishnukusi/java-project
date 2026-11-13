import{e as o,j as r,q as i,l as u,s as d,f as x,r as m}from"./index-C-FyqrFZ.js";/**
 * @license lucide-react v0.456.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const f=o("CircleHelp",[["circle",{cx:"12",cy:"12",r:"10",key:"1mglay"}],["path",{d:"M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3",key:"1u773s"}],["path",{d:"M12 17h.01",key:"p32p05"}]]);/**
 * @license lucide-react v0.456.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const p=o("CircleMinus",[["circle",{cx:"12",cy:"12",r:"10",key:"1mglay"}],["path",{d:"M8 12h8",key:"1wcyev"}]]);/**
 * @license lucide-react v0.456.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const g=o("CircleX",[["circle",{cx:"12",cy:"12",r:"10",key:"1mglay"}],["path",{d:"m15 9-6 6",key:"1uzhvr"}],["path",{d:"m9 9 6 6",key:"z0biqf"}]]);/**
 * @license lucide-react v0.456.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const b=o("Clock",[["circle",{cx:"12",cy:"12",r:"10",key:"1mglay"}],["polyline",{points:"12 6 12 12 16 14",key:"68esgv"}]]);/**
 * @license lucide-react v0.456.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const y=o("LoaderCircle",[["path",{d:"M21 12a9 9 0 1 1-6.219-8.56",key:"13zald"}]]);function k({title:e,description:t,actions:a}){return r.jsxs("div",{className:"mb-6 flex flex-wrap items-end justify-between gap-3",children:[r.jsxs("div",{children:[r.jsx("h1",{className:"font-display text-2xl font-semibold tracking-tight text-slate-900",children:e}),t&&r.jsx("p",{className:"mt-1 max-w-2xl text-sm text-slate-500",children:t})]}),a&&r.jsx("div",{className:"flex items-center gap-2",children:a})]})}function C({label:e,children:t,mono:a}){return r.jsxs("div",{children:[r.jsx("dt",{className:"label",children:e}),r.jsx("dd",{className:`mt-0.5 text-sm text-slate-800 ${a?"break-all font-mono text-xs":""}`,children:t})]})}function w({value:e,max:t=100,tone:a="bg-brand-500"}){const n=Math.max(0,Math.min(100,e/t*100));return r.jsx("div",{className:"h-1.5 w-full overflow-hidden rounded-full bg-slate-100",children:r.jsx("div",{className:`h-full rounded-full ${a} transition-[width] duration-500`,style:{width:`${n}%`}})})}const N={green:"bg-emerald-50 text-emerald-700 ring-emerald-600/20",amber:"bg-amber-50 text-amber-800 ring-amber-600/25",red:"bg-red-50 text-red-700 ring-red-600/20",slate:"bg-slate-100 text-slate-600 ring-slate-500/20",blue:"bg-sky-50 text-sky-700 ring-sky-600/20"},E={ACCEPT:["green",i,"Accept"],REVIEW:["amber",d,"Review"],QUARANTINE:["red",u,"Quarantine"],PASSED:["green",i,"Passed"],WARNING:["amber",d,"Warning"],FAILED:["red",g,"Failed"],PENDING:["slate",b,"Pending"],SKIPPED:["slate",p,"Skipped"],INFO:["blue",i,"Info"],LOW:["blue",d,"Low"],MEDIUM:["amber",d,"Medium"],HIGH:["red",g,"High"],NONE:["green",i,"None"],MODERATE:["amber",d,"Moderate"],SEVERE:["red",u,"Severe"],VERIFIED:["green",i,"Verified"],TAMPERED:["red",u,"Tampering detected"],UNAVAILABLE:["slate",f,"Verification unavailable"],BROKEN:["red",u,"Chain broken"],VALID:["green",i,"Chain intact"],READY:["green",i,"Ready"],GENERATING:["blue",y,"Generating"]};function A({value:e,label:t,className:a}){const[n,s,c]=E[e]||["slate",f,e??"—"];return r.jsxs("span",{className:x("inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset",N[n],a),children:[r.jsx(s,{size:12,className:e==="GENERATING"?"animate-spin":""}),t||c]})}function I({className:e,...t}){return r.jsx("div",{className:x("rounded-xl border border-slate-200 bg-white shadow-card",e),...t})}function v({className:e,title:t,subtitle:a,action:n,...s}){return r.jsxs("div",{className:x("flex items-start justify-between gap-4 border-b border-slate-100 px-5 py-4",e),...s,children:[r.jsxs("div",{children:[r.jsx("h3",{className:"text-sm font-semibold text-slate-900",children:t}),a&&r.jsx("p",{className:"mt-0.5 text-xs text-slate-500",children:a})]}),n]})}function M({className:e,...t}){return r.jsx("div",{className:x("p-5",e),...t})}function R(e,t=[]){const[a,n]=m.useState({data:null,loading:!0,error:null}),s=m.useRef(!0),c=m.useCallback(async()=>{n(l=>({...l,loading:!0,error:null}));try{const l=await e();s.current&&n({data:l,loading:!1,error:null})}catch(l){s.current&&n({data:null,loading:!1,error:l.message||"Something went wrong"})}},t);return m.useEffect(()=>(s.current=!0,c(),()=>{s.current=!1}),[c]),{...a,reload:c,setData:l=>n(h=>({...h,data:l}))}}function S(e,t=!0){return e?new Date(e).toLocaleString("en-GB",{day:"2-digit",month:"short",year:"numeric",...t?{hour:"2-digit",minute:"2-digit"}:{}}):"—"}const D=e=>e==null?"—":e.toLocaleString("en-US");function L(e,t=10,a=6){return e?e.length<=t+a+1?e:`${e.slice(0,t)}…${e.slice(-a)}`:"—"}export{I as C,C as K,w as M,k as P,A as S,v as a,M as b,D as c,S as f,L as t,R as u};
