#!/usr/bin/env python3
"""Read-only audit of existing masked datasets; writes derived diagnostics to --output.
No estimated-FDP scaling is applied: entrapment exchangeability is not established.
"""
import argparse,csv,hashlib,json,math,statistics
from collections import defaultdict
from pathlib import Path

def read(path):
    with path.open() as f:return list(csv.DictReader(f,delimiter='\t'))
def write(path,rows):
    with path.open('w') as f:
        w=csv.DictWriter(f,fieldnames=list(rows[0]),delimiter='\t');w.writeheader();w.writerows(rows)
def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(1048576),b''):h.update(b)
    return h.hexdigest()
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--repo',type=Path,default=Path(__file__).resolve().parents[2]);ap.add_argument('--output',type=Path,required=True);a=ap.parse_args()
    a.output.mkdir(parents=True,exist_ok=False)
    repo=a.repo.resolve();base=repo/'tmp/compatibility/reference-pep-20261011';source=repo/'tmp/compatibility/ten-file-20261010/10_file_example'
    specs=[('Context Percolator','percolator','.peptide.reference.txt'),('Standard Percolator','standard-percolator','.peptide.target.txt'),('Targeted Percolator','target-percolator','.peptide.target.txt'),('Context mProphet','context-mprophet','.peptide.reference.target.txt'),('Standard mProphet','standard-mprophet','.peptide.target.txt'),('Targeted mProphet','target-mprophet','.peptide.target.txt')]
    inputs={};counts=[];details=[];dist=[];inventory=[]
    def load(p):inputs[str(p.relative_to(repo))]=sha(p);return read(p)
    for n in range(1,4):
        prefix=f'cd8_2mz_gpf_masked{n}_assay';work=repo/'tmp/compatibility/all-workflows-20261011' if n==1 else base/f'dataset_{n}'
        result=work/'results'/prefix/'seed_1';manifest=result/'run.properties';assert 'status=COMPLETE' in manifest.read_text();inputs[str(manifest.relative_to(repo))]=sha(manifest)
        assay={}
        for r in load(source/(prefix+'.txt')):
            k=r['Compound']+'|'+str(int(r['z']));assert r['isDecoy'].lower() in ['true','false'];flag=r['isDecoy'].lower()=='true'
            assert k not in assay or assay[k][0]==flag
            assay[k]=(flag,float(r['RT Time (min)']),float(r['Window (min)']))
        refs={r['id']:r for r in load(work/'inputs'/(prefix+'_reference.features.txt.pairs.tsv')) if r['Label']=='1'}
        assert all(r['targetKey'] in assay for r in refs.values())
        features={}
        for population in ['reference','background']:
            rows=load(work/'inputs'/(prefix+'_'+population+'.features.txt')); values=defaultdict(list)
            for r in rows:
                val=float(r['primary']);assert math.isfinite(val);values[r['Label']].append(val)
                if population=='reference':features[r['id']]=r
            for label,v in values.items():
                q=statistics.quantiles(v,n=4,method='inclusive')
                dist.append(dict(dataset=n,population=population,search_label=label,n=len(v),primary_min=min(v),primary_q25=q[0],primary_median=q[1],primary_q75=q[2],primary_max=max(v)))
        inventory.append(dict(dataset=n,assay_targets=sum(not v[0] for v in assay.values()),assay_entrapments=sum(v[0] for v in assay.values()),surviving_target_precursors=sum(not assay[r['targetKey']][0] for r in refs.values()),surviving_entrapment_precursors=sum(assay[r['targetKey']][0] for r in refs.values())))
        for name,directory,suffix in specs:
            rows=[r for r in load(result/directory/(prefix+suffix)) if r.get('posterior_error_prob') is not None];byid={r.get('id',r.get('PSMId')):r for r in rows};assert len(rows)==len(byid)
            assert set(refs)<=set(byid),(n,name,'missing reference ids')
            # Collapse charge states by minimum reported value independently for each metric.
            pep={};qval={};classes={}
            for ident,meta in refs.items():
                r=byid[ident];key=meta['targetKey'];seq=key.rsplit('|',1)[0];flag,center,width=assay[key]
                p=float(r['posterior_error_prob']);q=float(r.get('q-value',r.get('q_value','nan')))
                assert all(math.isfinite(v) and 0<=v<=1 for v in [p,q]);assert seq not in classes or classes[seq]==flag
                classes[seq]=flag;pep[seq]=min(p,pep.get(seq,1));qval[seq]=min(q,qval.get(seq,1))
                rt=float(features[ident]['RTinMin']);inside=abs(rt-center)<=width/2
                details.append(dict(dataset=n,workflow=name,feature_id=ident,modified_peptide=seq,assay_entrapment=flag,PEP=p,q_value=q,selected_RT_min=rt,assay_RT_min=center,assay_window_min=width,inside_assay_RT_window=inside))
            for metric,values in [('PEP',pep),('q_value',qval)]:
                for threshold in [.001,.005,.01,.02,.05,.1]:
                    passing=[k for k,v in values.items() if v<=threshold];e=sum(classes[k] for k in passing);t=len(passing)-e
                    counts.append(dict(dataset=n,workflow=name,metric=metric,threshold=threshold,non_entrapment_peptides=t,entrapment_peptides=e,observed_entrapment_share=e/len(passing) if passing else '',reported_population='combined (reference subset shown)' if name.startswith('Standard') else 'reference',interpretation='descriptive share; not estimated FDP'))
    # Guard against changing any source while auditing.
    assert all(sha(repo/p)==h for p,h in inputs.items())
    write(a.output/'threshold_counts.tsv',counts);write(a.output/'reference_peptide_evidence.tsv',details);write(a.output/'primary_score_distributions.tsv',dist);write(a.output/'assay_inventory.tsv',inventory)
    (a.output/'input_sha256.json').write_text(json.dumps(inputs,indent=2)+'\n')
    print('Validated',len(inputs),'source artifacts;',len(counts),'threshold summaries;',len(details),'reference report rows')
    for r in counts:
        if r['metric']=='PEP' and r['threshold']==.01:print(r['dataset'],r['workflow'],'T',r['non_entrapment_peptides'],'E',r['entrapment_peptides'],'share',r['observed_entrapment_share'])
    for n in range(1,4):
        r=[x for x in details if x['dataset']==n and x['workflow']=='Context Percolator' and x['assay_entrapment'] and x['PEP']<=.01]
        print('Passing Context Percolator entrapment precursors inside/outside own library-RT window',n,sum(x['inside_assay_RT_window'] for x in r),sum(not x['inside_assay_RT_window'] for x in r))
if __name__=='__main__':main()
