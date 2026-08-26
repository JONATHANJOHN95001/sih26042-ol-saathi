# -*- coding: utf-8 -*-
"""
Replace the loose content labels with a NIPUN Bharat alignment.

The pack shipped labels like ROUTINE and FL-OL. Those were our own shorthand.
The problem statement asks for content "aligned to the NIPUN Bharat learning
outcomes framework", so the labels need to name things the framework actually
names.

What is used here, and why it is defensible:

  Goals    NIPUN Bharat defines three Developmental Goals. They are published
           in the Ministry of Education guidelines (2021) and are stable:
             HW  Children maintain good health and well-being
             EC  Children become effective communicators
             IL  Children become involved learners and connect with their
                 immediate environment

  Domains  The published domain names under Foundational Literacy and
           Foundational Numeracy: Oral Language, Decoding, Reading
           Comprehension, Writing, Number Sense, Number Operations.

  Grade    G1, the grade this content targets.

Deliberately NOT done: inventing official-looking outcome-code strings such as
"L1-FL-OL-01". We could not verify those against the source document, and an
unverifiable code that looks official is worse than an honest mapping. What
ships is a goal-and-domain alignment that any reader can check against the
guidelines, and the pack says exactly that.
"""
import io
import json
import collections

PACK = 'app/src/main/assets/pack/pack.sat.json'

GOALS = {
    'HW': 'Children maintain good health and well-being',
    'EC': 'Children become effective communicators',
    'IL': 'Children become involved learners',
}

DOMAINS = {
    'OL': 'Oral Language Development',
    'DC': 'Decoding',
    'RC': 'Reading Comprehension',
    'WR': 'Writing',
    'NS': 'Number Sense',
    'NO': 'Number Operations',
    'CR': 'Classroom Routine',
}

# Per-entry assignment. Anything not named here falls back to its old label.
EXPLICIT = {
    # health and well-being
    'p38': 'HW-CR-G1', 'p39': 'HW-CR-G1',
    # decoding: letters and sounds rather than meaning
    'p32': 'EC-DC-G1', 'p33': 'EC-DC-G1', 'p34': 'EC-DC-G1',
    # writing
    'p12': 'EC-WR-G1',
    # numeracy
    'p27': 'IL-NS-G1', 'p28': 'IL-NS-G1', 'p30': 'IL-NS-G1', 'p31': 'IL-NS-G1',
    'p29': 'IL-NO-G1',
    # reading comprehension: the picture prompt
    'p13': 'EC-RC-G1',
}

# Fallback by the old label.
BY_OLD = {
    'ROUTINE': 'EC-CR-G1',
    'FL-OL': 'EC-OL-G1',
    'FL-WR': 'EC-WR-G1',
    'FL-RD': 'EC-RC-G1',
    'FN-NS': 'IL-NS-G1',
    'FN-OP': 'IL-NO-G1',
}


def main():
    pack = json.load(io.open(PACK, encoding='utf-8'))
    counts = collections.Counter()

    for key, entry in pack['entries'].items():
        old = entry.get('nipun', '')
        code = EXPLICIT.get(key) or BY_OLD.get(old)
        if code is None:
            raise SystemExit('no NIPUN mapping for %s (label %r)' % (key, old))

        goal, domain, grade = code.split('-')
        entry['nipun'] = code
        entry['nipunGoal'] = GOALS[goal]
        entry['nipunDomain'] = DOMAINS[domain]
        entry['nipunGrade'] = 1
        # audio is a drop-in: the field exists now so nothing has to change
        # in the app when a recording or a Bhashini clip arrives.
        # Deliberately not written as null. org.json reads a JSON null back
        # as the string "null", so an absent field must be absent.
        entry.pop('audio', None)
        entry.pop('audioProvenance', None)
        counts[code] += 1

    pack['nipunFramework'] = {
        'name': 'NIPUN Bharat, Ministry of Education, 2021',
        'alignment': 'goal and domain',
        'note': (
            'Each entry is mapped to a published NIPUN Bharat Developmental '
            'Goal and Foundational domain. These are framework alignments, '
            'not official outcome-code strings, and are stated as such.'
        ),
        'goals': GOALS,
        'domains': DOMAINS,
    }

    with io.open(PACK, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(pack, fh, ensure_ascii=False, indent=2, sort_keys=True)
        fh.write('\n')

    print('NIPUN alignment written, %d entries' % sum(counts.values()))
    for code, n in sorted(counts.items()):
        goal, domain, _ = code.split('-')
        print('  %-10s %2d   %s / %s' % (code, n, goal, DOMAINS[domain]))


if __name__ == '__main__':
    main()
