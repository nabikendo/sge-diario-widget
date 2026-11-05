'use strict';
const { lerBackup, valor, estado, NOMES } = window.requireModel || { lerBackup: window.lerBackup, valor: window.valor, estado: window.estado, NOMES: window.NOMES };
let backup = null;
const el = id => document.getElementById(id);
function render() {
  const base = backup?.[el('panel').value] || [];
  const q = el('search').value.trim().toLocaleLowerCase('pt-BR');
  const filtro = el('status').value;
  const rows = base.filter(r => (!filtro || estado(r) === filtro) && (!q || [r.nome,r.escola,r.disciplina].join(' ').toLocaleLowerCase('pt-BR').includes(q)));
  el('summary').replaceChildren();
  for (const [label,count] of [['Resultados',rows.length],['Pendentes',rows.filter(r=>estado(r)==='pendente').length],['A conferir',rows.filter(r=>estado(r)==='conferir').length],['Em dia',rows.filter(r=>estado(r)==='em dia').length]]) {
    const box=document.createElement('div');const number=document.createElement('b');number.textContent=count;box.append(number,document.createTextNode(label));el('summary').append(box);
  }
  el('cards').replaceChildren();
  if (!rows.length) { const p=document.createElement('p');p.id='empty';p.textContent=backup?'Nenhum resultado neste filtro.':'Importe um backup para ver o painel.';el('cards').append(p); }
  for (const r of rows) {
    const article=document.createElement('article'),badge=document.createElement('span'),title=document.createElement('h2'),sub=document.createElement('p'),table=document.createElement('table');
    const s=estado(r);badge.className='badge '+(s==='em dia'?'emdia':s);badge.textContent=s;title.textContent=r.nome;sub.className='subtitle';sub.textContent=[r.disciplina,r.escola,r.atualizado&&'Consulta: '+r.atualizado].filter(Boolean).join(' · ');
    article.append(badge,title,sub);
    r.modulos.forEach((m,i)=>{const tr=document.createElement('tr'),name=document.createElement('td'),n=document.createElement('td');name.textContent=NOMES[i];n.textContent=valor(m);n.title=m.detalhe;tr.append(name,n);table.append(tr);});
    article.append(table);el('cards').append(article);
  }
}
el('open').onclick=async()=>{try{el('message').textContent='';const raw=await window.sgeDesktop.abrirBackup();if(raw===null)return;backup=lerBackup(raw);el('source').textContent=`Backup ${backup.versao} · ${backup.criado} · dados locais da última consulta`;el('pdf').disabled=false;render();}catch(e){el('message').textContent=e.message;}};
el('sge').onclick=()=>window.sgeDesktop.abrirSge();
el('pdf').onclick=async()=>{try{el('message').textContent='';await window.sgeDesktop.salvarPdf();}catch(e){el('message').textContent=e.message;}};
['panel','status','search'].forEach(id=>el(id).addEventListener(id==='search'?'input':'change',render));
render();
