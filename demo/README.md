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

`run.cmd` copies this file to `data/snapshot.json` when no snapshot exists. To
start from an empty store, delete `data/snapshot.json` and use `--init-accounts`
or `--accounts`.

These are demonstration credentials only; they are not defaults and the
application never creates accounts on its own.
