from pathlib import Path

files = [
    'app/src/test/java/com/example/data/BankBackup7AcceptanceTest.kt',
    'app/src/test/java/com/example/data/BankLearningRulesBackupAcceptanceTest.kt',
    'app/src/test/java/com/example/data/BankPhase2CBackupAcceptanceTest.kt',
    'app/src/test/java/com/example/data/SupplementalDriveBackupTest.kt',
]
for name in files:
    p = Path(name)
    s = p.read_text()
    old = 'assertEquals(10, payload.getInt("schemaVersion"))'
    if old not in s:
        raise SystemExit(f'missing schema assertion in {name}')
    p.write_text(s.replace(old, 'assertEquals(11, payload.getInt("schemaVersion"))', 1))

p = Path('app/src/test/java/com/example/data/BankMigration24AcceptanceTest.kt')
s = p.read_text()
s = s.replace('@Test fun freshDatabaseIsVersion27()', '@Test fun freshDatabaseIsVersion28()', 1)
s = s.replace('assertEquals(27, database.openHelper.writableDatabase.version)', 'assertEquals(28, database.openHelper.writableDatabase.version)', 1)
p.write_text(s)
print('updated schema expectations only')
