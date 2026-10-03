# Typing asset sources

Dakshina Malayalam lexicons are attributed and licensed in
[NOTICE.txt](../../app/src/main/assets/dictionaries/manglish/NOTICE.txt) and
[CC BY-SA 4.0](../../app/src/main/assets/dictionaries/LICENSE-CC-BY-SA-4.0.txt).
Source and derived-asset hashes are in the bundled Manglish provenance manifest.
`manglish-conversational-variants.json` supplements those attested forms.
`manglish-conversations.tsv` contains authored fictional conversations partitioned
by template family; no user messages are used.

`runtime-ranking-training.jsonl.gz` is a frozen, canonical JSON export from
`ManglishEvaluationTest.exportRuntimeTrainingCompetition`. It contains training
families only and the numeric features used by the runtime ranker. Regeneration
requires running that worker test, sorting rows by language/family/typed/context,
and canonical JSON with a gzip timestamp of zero. The trainer consumes this frozen
competition, and a separate test verifies its vectors against current runtime
features. No holdout or calibration examples train these weights.
