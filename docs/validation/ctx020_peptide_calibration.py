#!/usr/bin/env python3
"""Reproduce supplied Figure 09 R binning/bounds on annotated peptide outputs.
Input TSV: period, workflow, iteration, key, score, pep, entrapment,
scheduled_targets, scheduled_entrapments. One best peptide/charge per run.
"""
import argparse,bisect,csv,json,math,statistics
from collections import defaultdict
from pathlib import Path
from scipy.stats import beta

EDGES=[round((i/10)**2,8) for i in range(11)]
def bin_index(pep):
    if not math.isfinite(pep) or not 0<=pep<=1:return None
    return min(bisect.bisect_right(EDGES,pep)-1,9)
def estimates(e,t,factor):
    n=e+t
    if not n:return (math.nan,)*4
    lower=e/n
    lo=0 if e==0 else beta.ppf(.025,e,n-e+1)
    hi=1 if e==n else beta.ppf(.975,e+1,n-e)
    return lower,min(lower*factor,1),float(lo),min(float(hi)*factor,1)
def write(path,rows,fields=None):
    with path.open('w') as f:
        w=csv.DictWriter(f,fieldnames=fields or list(rows[0]),delimiter='\t');w.writeheader();w.writerows(rows)
def main():
    p=argparse.ArgumentParser();p.add_argument('--input',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--before-label',default='before');p.add_argument('--after-label',default='after');p.add_argument('--note',default='');a=p.parse_args()
    a.output.mkdir(parents=True,exist_ok=False)
    with a.input.open() as f:rows=list(csv.DictReader(f,delimiter='\t'))
    selected={};coverage=defaultdict(lambda:defaultdict(int))
    for r in rows:
        r=dict(r);r['pep']=float(r['pep']);r['score']=float(r['score']);r['entrapment']=r['entrapment'].lower()=='true'
        t,e=int(r['scheduled_targets']),int(r['scheduled_entrapments'])
        if t<=0 or e<=0:raise ValueError('Both scheduled class counts must be positive')
        r['factor']=1+t/e
        if not math.isfinite(r['score']):
            c=coverage[(r['period'],r['workflow'])];c['peptides']+=1;c['nonfinite_score']+=1
            c['nonfinite_pep']+=int(not math.isfinite(r['pep']))
            continue
        k=tuple(r[c] for c in ['period','workflow','iteration','key'])
        rank=lambda x:(-x['score'],x['pep'] if math.isfinite(x['pep']) else math.inf)
        if k not in selected or rank(r)<rank(selected[k]):selected[k]=r
    groups=defaultdict(list)
    for r in selected.values():
        g=(r['period'],r['workflow']);coverage[g]['peptides']+=1
        i=bin_index(r['pep'])
        if i is None:
            coverage[g]['nonfinite_pep' if not math.isfinite(r['pep']) else 'out_of_range_pep']+=1
        else:groups[g+(i,)].append(r);coverage[g]['binned_peptides']+=1
    result=[]
    for (period,workflow,i),rs in sorted(groups.items()):
        es=[r for r in rs if r['entrapment']];e=len(es);t=len(rs)-e
        factor=statistics.mean(r['factor'] for r in (es or rs))
        low,high,ci_lo,ci_hi=estimates(e,t,factor)
        result.append(dict(period=period,workflow=workflow,bin=i+1,bin_left=EDGES[i],bin_right=EDGES[i+1],right_inclusive=i==9,iterations=len({r['iteration'] for r in rs}),n_targets=t,n_entrapments=e,n_accepted=len(rs),mean_pep=statistics.mean(r['pep'] for r in rs),upper_factor=factor,fdp_lower=low,fdp_upper=high,ci_95_low=ci_lo,ci_95_high=ci_hi))
    coverage_rows=[]
    for (period,w),c in sorted(coverage.items()):
        coverage_rows.append(dict(period=period,workflow=w,peptides=c['peptides'],binned_peptides=c['binned_peptides'],nonfinite_pep=c['nonfinite_pep'],nonfinite_score=c['nonfinite_score'],out_of_range_pep=c['out_of_range_pep'],filled_bins=sum(r['period']==period and r['workflow']==w for r in result)))
    write(a.output/'peptide-calibration-by-bin.tsv',result);write(a.output/'coverage.tsv',coverage_rows)
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from matplotlib.ticker import PercentFormatter
    names={'percolator':'Context + Percolator','standard-percolator':'Percolator (standard)','target-percolator':'Percolator (targeted)'}
    colors={'before':'#0072B2','after':'#D55E00'}
    plt.rcParams.update({'font.size':10,'pdf.fonttype':42})
    for limit,suffix in [(1,'full'),(.3,'zoom')]:
        fig,axes=plt.subplots(2,3,figsize=(13,8),sharex=True,sharey=True)
        fig.subplots_adjust(top=.86,bottom=.17,hspace=.35,wspace=.22)
        for j,period in enumerate(colors):
            for ax,w in zip(axes[j],names):
                rs=[r for r in result if r['period']==period and r['workflow']==w]
                ax.plot([0,limit],[0,limit],':',color='gray');ax.set_title(f'{names[w]}\n'+(a.before_label if period=='before' else a.after_label),fontsize=11)
                for r in rs:
                    x=r['mean_pep'];ax.vlines(x,r['ci_95_low'],r['ci_95_high'],color=colors[period],alpha=.45,lw=1)
                    ax.vlines(x,r['fdp_lower'],r['fdp_upper'],color=colors[period],alpha=.3,lw=5)
                    ax.scatter(x,r['fdp_lower'],marker='o',facecolors='white',edgecolors=colors[period],s=35,zorder=3)
                    ax.scatter(x,r['fdp_upper'],marker='^',color=colors[period],s=25,zorder=4)
                if not rs:ax.text(.5,.5,'No valid scored peptides\nSee exclusions in coverage.tsv',ha='center',transform=ax.transAxes)
                ax.set_xlim(-.015*limit,limit);ax.set_ylim(-.025*limit,limit);ax.grid(alpha=.15)
                ax.xaxis.set_major_formatter(PercentFormatter(1));ax.yaxis.set_major_formatter(PercentFormatter(1))
        for ax in axes[1]:ax.set_xlabel('Mean peptide PEP within bin')
        for ax in axes[:,0]:ax.set_ylabel('Entrapment FDP within bin')
        fig.suptitle('Seed 23 • peptide-level PEP calibration using supplied R formulas',fontsize=15)
        fig.text(.08,.095,'○ Lower = E/(T+E)    ▲ Upper = min[lower × 1.2, 1]    Thin lines: R-style binomial intervals',fontsize=10)
        fig.text(.08,.06,'Ten bins at (k/10)²; each bin is independent. No cumulative thresholding. Sparse-bin counts are in the TSV.',fontsize=10)
        fig.text(.08,.025,a.note or 'Descriptive entrapment bounds; interpretation depends on acquisition coverage and entrapment assumptions.',fontsize=9)
        for ext in ['png','pdf','svg']:fig.savefig(a.output/f'peptide-binned-fdp-{suffix}.{ext}',dpi=180)
        plt.close(fig)
    print(json.dumps(coverage_rows,indent=2))
if __name__=='__main__':main()
