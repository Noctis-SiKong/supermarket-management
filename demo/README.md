# Demo preset

`preset_snapshot_20261022.json` is a valid `data/snapshot.json` produced by the
real application (accounts, products, opening balance, one approved receipt and
three paid sales). It exists so that a fresh checkout or submission ZIP can start
with a populated store instead of `No accounts configured`.

| Username | Password | Role |
|---|---|---|
| `manager01` | `demo1234` | MANAGER |
| `clerk01` | `demo1234` | CLERK |
| `admin01` | `demo1234` | ADMIN |

The `run.cmd`, `run.sh` and `run.command` launchers import the demo snapshot when `data/snapshot.json` is absent. An existing `data/previous.json` can also cause recovery to block writing; removing only the current snapshot is not an empty-store procedure.

To start with an empty store, first back up the **entire existing `data/` directory**, including the previous snapshot and attachments. Prepare a separate empty project root with real `pom.xml` and `README.md` files and a `docs/` directory; an arbitrary empty directory fails the root-marker check. Keep its `data/` absent or empty, and launch the existing executable JAR directly rather than through a seeding launcher. In the examples, replace the placeholders with absolute paths; never point an empty-store exercise at real data:

```powershell
java '-Dfile.encoding=UTF-8' -jar '<path-to-jar-with-dependencies.jar>' --project-root '<isolated-project-root>' --init-accounts
java '-Dfile.encoding=UTF-8' -jar '<path-to-jar-with-dependencies.jar>' --project-root '<isolated-project-root>' --accounts '<local-hashed-accounts.json>'
```

`--init-accounts` interactively creates the three role accounts and requires a real console; it cannot be combined with `--accounts` or an existing current snapshot. `--accounts` instead reads a local JSON array of already hashed credentials; it does not prompt for account setup. Keep this file local and outside Git. For a non-interactive root/storage check, append `--check`; this does not exercise the interactive setup. The direct `--check` and `--accounts ... --check` paths were checked with an isolated marked root and Java 21; interactive `--init-accounts` was not verified in this documentation revision.

These are demonstration credentials only; they are not defaults and the
application never creates accounts on its own.
