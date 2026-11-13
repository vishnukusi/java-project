(async()=>{const{el,num,decision,check,badge,fmtTime,banner,page}=SCV;
 const d=JSON.parse(document.getElementById('__DATA__').textContent),a=d.assessment||{};
 banner(document.getElementById('msg'),d.error?'err':'ok',d.error||d.success);
 let completed='—';
 try{
   const h=await page('/history');
   const list=Array.isArray(h)?h:(h&&h.historyList?h.historyList:[]);
   completed=num(list.filter(x=>x.status==='COMPLETED').length);
 }catch{}
 const hm=(a.highCount!=null&&a.mediumCount!=null)?a.highCount+a.mediumCount:null;
 const S=[['Registered assets',num((d.totalDatasets||0)+(d.totalModels||0)),`${num(d.totalDatasets)} datasets, ${num(d.totalModels)} models`],
  ['Completed audits',completed,'From scan history'],['Total findings',num(d.totalFindings),'All modules'],
  ['Critical findings',num(d.criticalFindings),''],['High + Medium findings',num(hm),'Need review']];
 document.getElementById('stats').replaceChildren(...S.map(([l,v,c])=>el('div',{class:'card stat'},el('div',{class:'l'},l),el('div',{class:'v'},v),el('div',{class:'c'},c))));
 const ac=document.getElementById('assess');ac.replaceChildren(el('div',{class:'l'},'System decision'),el('div',{style:'margin:8px 0'},decision(a.overallDecision)),
  ...(a.decisionJustifications||[]).map(j=>el('p',{class:'c',style:'margin-top:4px'},j)));
 const names={'dataset.integrity':'Dataset integrity','model.baseline_inference':'Model baseline inference','model.trigger_perturbation':'Model trigger perturbation','provenance.verification':'Inference provenance'};
 const cs=a.checkStatuses||{};
 document.getElementById('checks').replaceChildren(...Object.entries(names).map(([k,n])=>el('li',{},n,check(cs[k]))));
 const r=document.getElementById('recent'),rows=d.recentAudits||[];
 r.replaceChildren(rows.length?el('div',{class:'tw'},el('table',{class:'tbl stack'},el('thead',{},el('tr',{},...['Type','Asset','Findings','Assessment','Completed'].map(h=>el('th',{},h)))),
  el('tbody',{},...rows.map(x=>el('tr',{},el('td',{'data-l':'Type'},x.auditType||'—'),el('td',{'data-l':'Asset'},x.targetName||'—'),el('td',{class:'num','data-l':'Findings'},num(x.findingsCount)),el('td',{'data-l':'Assessment'},decision(x.assessmentDecision)),el('td',{'data-l':'Completed'},fmtTime(x.completedAt)))))))
  :el('div',{class:'empty'},'No scans yet. ',el('a',{href:'/scan'},'Start a scan'),' to see results here.'));
})();

