import{e as o,j as s,f as n,r as p,p as d}from"./index-C-FyqrFZ.js";/**
 * @license lucide-react v0.456.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const u=o("ArrowUpDown",[["path",{d:"m21 16-4 4-4-4",key:"f6ql7i"}],["path",{d:"M17 20V4",key:"1ejh1v"}],["path",{d:"m3 8 4-4 4 4",key:"11wl7u"}],["path",{d:"M7 4v16",key:"1glfcx"}]]);/**
 * @license lucide-react v0.456.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const m=o("ArrowUp",[["path",{d:"m5 12 7-7 7 7",key:"hav0vg"}],["path",{d:"M12 19V5",key:"x0mq9r"}]]);/**
 * @license lucide-react v0.456.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const f=o("Search",[["circle",{cx:"11",cy:"11",r:"8",key:"4ej97u"}],["path",{d:"m21 21-4.3-4.3",key:"1qie3q"}]]),i="h-9 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-800 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500/40 focus:border-brand-500 transition",h=p.forwardRef(function({className:e,...a},c){return s.jsx("input",{ref:c,className:n(i,e),...a})});function j({className:t,children:e,...a}){return s.jsx("select",{className:n(i,"pr-8",t),...a,children:e})}function w({label:t,hint:e,children:a}){return s.jsxs("label",{className:"block space-y-1.5",children:[s.jsx("span",{className:"text-xs font-medium text-slate-700",children:t}),a,e&&s.jsx("span",{className:"block text-[11px] text-slate-500",children:e})]})}function N({value:t,onChange:e,placeholder:a="Search…",className:c}){return s.jsxs("div",{className:n("relative w-full max-w-xs",c),children:[s.jsx(f,{size:15,className:"pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"}),s.jsx(h,{value:t,onChange:l=>e(l.target.value),placeholder:a,className:"pl-9","aria-label":a})]})}function y({label:t,sortKey:e,sort:a,onSort:c,className:l}){const r=(a==null?void 0:a.key)===e,x=r?a.dir==="asc"?m:d:u;return s.jsx("th",{scope:"col","aria-sort":r?a.dir==="asc"?"ascending":"descending":"none",className:n("px-4 py-2.5 text-left",l),children:s.jsxs("button",{onClick:()=>c(e),className:"label inline-flex items-center gap-1 hover:text-slate-800",children:[t,s.jsx(x,{size:12,className:r?"text-brand-600":"text-slate-300"})]})})}function k({children:t,className:e}){return s.jsx("th",{scope:"col",className:n("label px-4 py-2.5 text-left",e),children:t})}function v({children:t,className:e}){return s.jsx("div",{className:n("overflow-x-auto",e),children:s.jsx("table",{className:"w-full min-w-[640px] border-collapse text-sm",children:t})})}export{w as F,h as I,j as S,v as T,N as a,y as b,k as c};
