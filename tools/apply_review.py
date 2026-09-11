# -*- coding: utf-8 -*-
"""
Apply a Santali speaker's review to the pack.

The review sheet exports santali-review.json. This reads it, applies the
corrections, and records who did the review and when.

That last part is the point. Once a named human has checked a string, the pack
can honestly say so, and the app's provenance chip stops reading
"Machine translation" for those entries. That is the difference between a claim
we can defend and one we cannot, so it is recorded per entry rather than as a
blanket statement over the whole pack.

    python tools/apply_review.py path/to/santali-review.json
    python tools/apply_review.py path/to/santali-review.json --dry-run

Nothing is written without --write, so you can always look first.
"""
import json
import pathlib
import shutil
import sys
from datetime import datetime, timezone

REPO = pathlib.Path(__file__).resolve().parent.parent
PACK = REPO / 'app' / 'src' / 'main' / 'assets' / 'pack' / 'pack.sat.json'


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    review_path = pathlib.Path(argv[0])
    write = '--write' in argv
    if not review_path.exists():
        print('no such file: %s' % review_path)
        return 2

    review = json.loads(review_path.read_text(encoding='utf-8'))
    pack = json.loads(PACK.read_text(encoding='utf-8'))
    entries = pack['entries']

    reviewer = review.get('reviewer', {})
    name = (reviewer.get('name') or '').strip()
    if not name:
        print('The review has no reviewer name. Refusing to record an anonymous')
        print('verification, because "verified by nobody in particular" is not a')
        print('claim worth making. Add a name and rerun.')
        return 1

    when = (review.get('reviewedAt') or datetime.now(timezone.utc).isoformat())[:10]
    verdicts = review.get('verdicts', {})

    confirmed, corrected, rejected, unknown = [], [], [], []

    for eid, v in verdicts.items():
        if eid not in entries:
            unknown.append(eid)
            continue
        verdict = v.get('verdict')
        fix = (v.get('fix') or '').strip()
        if verdict == 'ok':
            confirmed.append(eid)
        elif verdict == 'bad' and fix:
            corrected.append((eid, entries[eid]['target'], fix))
        elif verdict == 'bad':
            rejected.append(eid)

    print('reviewer   : %s' % name)
    if reviewer.get('note'):
        print('background : %s' % reviewer['note'])
    print('reviewed   : %s' % when)
    print()
    print('confirmed correct        : %d' % len(confirmed))
    print('corrected (replacement)  : %d' % len(corrected))
    print('marked wrong, no fix     : %d  -> these will be REMOVED' % len(rejected))
    print('unknown ids, ignored     : %d' % len(unknown))
    print('not reviewed             : %d' % (len(entries) - len(verdicts)))

    if corrected:
        print()
        print('Corrections:')
        for eid, before, after in corrected[:10]:
            print('  %-16s' % eid)
            print('    was : %s' % before)
            print('    now : %s' % after)
    if rejected:
        print()
        print('Removing these, because a wrong translation with no replacement is')
        print('worse than none. The app shows "Not in the offline pack" instead:')
        for eid in rejected[:10]:
            print('  %-16s %s' % (eid, entries[eid].get('en', '')))

    if not write:
        print()
        print('Nothing written. Rerun with --write to apply.')
        return 0

    backup = PACK.with_suffix('.json.bak')
    shutil.copy2(PACK, backup)

    for eid in confirmed:
        entries[eid]['reviewedBy'] = name
        entries[eid]['reviewedOn'] = when
        entries[eid]['reviewVerdict'] = 'confirmed'
    for eid, _before, after in corrected:
        entries[eid]['target'] = after
        entries[eid]['reviewedBy'] = name
        entries[eid]['reviewedOn'] = when
        entries[eid]['reviewVerdict'] = 'corrected'
        entries[eid]['service'] = '%s (corrected by %s)' % (entries[eid].get('service', ''), name)
    for eid in rejected:
        del entries[eid]

    pack.setdefault('provenance', {})['humanReview'] = {
        'reviewer': name,
        'contact': reviewer.get('contact', ''),
        'background': reviewer.get('note', ''),
        'date': when,
        'confirmed': len(confirmed),
        'corrected': len(corrected),
        'removed': len(rejected),
        'unreviewed': len(entries) - len(confirmed) - len(corrected),
    }

    PACK.write_text(json.dumps(pack, ensure_ascii=False, indent=2), encoding='utf-8')
    print()
    print('written. backup at %s' % backup.name)
    print('%d entries remain.' % len(entries))
    print()
    print('Now run the tests, they check the shipped pack:')
    print('  ./gradlew :app:testDebugUnitTest')
    print()
    print('Entries this person confirmed or corrected can honestly be called')
    print('verified. The rest cannot, and the pack now distinguishes them.')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
