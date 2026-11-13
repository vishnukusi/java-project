(()=>{const{el,post,page,banner}=SCV,$=s=>document.querySelector(s),$$=s=>[...document.querySelectorAll(s)];
 const msg=$('#msg');
 const ext=f=>(f.name.match(/\.[^.]+$/)||[''])[0].toLowerCase();
 const TS=['.pt','.pth','.torchscript'];
 const rules={
  dataset:f=>!f?'Choose a .zip archive.':f.size===0?'The file is empty.':ext(f)!=='.zip'?'Only .zip archives are accepted.':'',
  model:(f,form)=>{const k=form.format.value;return !f?'Choose a model file.':f.size===0?'The file is empty.':
    k==='ONNX'&&ext(f)!=='.onnx'?'ONNX models must be .onnx files.':k==='TORCHSCRIPT'&&!TS.includes(ext(f))?'TorchScript models must be .pt, .pth or .torchscript files.':''},
  inference:(f,form)=>f?(f.size===0?'The file is empty.':ext(f)!=='.json'?'Only .json files are accepted.':''):(form.jsonContent.value.trim()?'':'Choose a .json file or paste JSON records.')};
 function wire(form){const type=form.dataset.type,btn=form.querySelector('[type=submit]'),why=form.querySelector('.why'),inv=form.querySelector('.invalid'),
   fin=form.querySelector('input[type=file]'),fn=form.querySelector('.fn'),drop=form.querySelector('.drop'),bar=form.querySelector('.bar i');
  const check=()=>{if(type==='drift'){const a=form.referenceDatasetId.value,b=form.evaluationDatasetId.value;const r=!a||!b?'Select a reference and a new dataset.':a===b?'The two datasets must be different.':'';why.textContent=r;btn.disabled=!!r;return}
   const f=fin.files[0],r=rules[type](f,form);fn&&(fn.textContent=f?f.name:'');inv.textContent=f?r:'';why.textContent=r;btn.disabled=!!r};
  form.addEventListener('input',check);form.addEventListener('change',check);
  if(drop){['dragenter','dragover'].forEach(e=>drop.addEventListener(e,x=>{x.preventDefault();drop.classList.add('over')}));
   ['dragleave','drop'].forEach(e=>drop.addEventListener(e,x=>{x.preventDefault();drop.classList.remove('over')}));
   drop.addEventListener('drop',x=>{if(x.dataTransfer.files.length){fin.files=x.dataTransfer.files;check()}})}
  check();
  form.addEventListener('submit',async e=>{e.preventDefault();banner(msg);const lab=btn.textContent;btn.disabled=true;btn.textContent='Working…';
   const status=t=>{form.querySelector('.st').textContent=t};
   try{
    if(type==='dataset'||type==='model'){const k=type==='dataset'?'datasets':'models';
     status('Uploading…');const up=await post(`/${k}/upload`,new FormData(form),p=>{bar.style.width=Math.round(p*100)+'%'});
     const m=up.path.match(new RegExp(`^/${k}/(\\d+)$`));if(!m)throw new Error((up.data&&up.data.error)||'The server rejected the upload.');
     bar.style.width='0';status('Uploaded and registered. Starting audit…');
     const au=await post(`/${k}/${m[1]}/audit`);if(!new RegExp(`^/${k}/\\d+/audits/\\d+$`).test(au.path))throw new Error(((au.data&&au.data.error)||'The audit could not be started.')+' The upload itself succeeded and the asset is registered.');
     location.href=au.path;return}
    if(type==='inference'){status('Importing and verifying…');const r=await post('/inference/import',new FormData(form));
     if(r.data&&r.data.error)throw new Error(r.data.error);
     banner(msg,'ok',(r.data&&r.data.success)||'Import finished.');
     const bn=msg.querySelector('.banner');if(bn){bn.append(' ',el('a',{href:'/inference',style:'color:inherit;text-decoration:underline;margin-left:8px'},'View inference records'))}else{msg.append(el('a',{href:'/inference'},'View inference records'))}
     status('')}
    if(type==='drift'){status('Comparing datasets (this request completes when the analysis is done)…');const r=await post('/drift/run',new FormData(form));
     if(!/^\/drift\/\d+$/.test(r.path))throw new Error((r.data&&r.data.error)||'The analysis could not be run.');location.href=r.path;return}
   }catch(x){banner(msg,'err',x.message);form.querySelector('.st').textContent=''}
   btn.textContent=lab;check()});}
 $$('form[data-type]').forEach(wire);
 const tabs=$$('.tile');const show=t=>{tabs.forEach(b=>b.setAttribute('aria-selected',b.dataset.t===t));$$('form[data-type]').forEach(f=>f.hidden=f.dataset.type!==t);banner(msg)};
 tabs.forEach(b=>b.addEventListener('click',()=>show(b.dataset.t)));show('dataset');
 $('#reverify').addEventListener('click',async e=>{const b=e.target;b.disabled=true;banner(msg);try{const r=await post('/inference/verify-all');banner(msg,r.data&&r.data.error?'err':'ok',(r.data&&(r.data.error||r.data.success))||'Done.')}catch(x){banner(msg,'err',x.message)}b.disabled=false});
 (async()=>{const f=document.forms.drift;try{const d=(await page('/datasets')).datasets||[];
  for(const s of[f.referenceDatasetId,f.evaluationDatasetId])s.append(...d.map(x=>el('option',{value:x.id},`${x.name} (${x.format}, #${x.id})`)));
  $('#nods').hidden=d.length>1}catch(x){banner(msg,'err','Could not load datasets: '+x.message)}})();
})();

