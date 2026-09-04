Function reference
==================

Version-stamped, one item per row. Sources: `src/Latte/Runtime/Defaults.php`
(`getFunctions()`) at `v2.11.7`, and `src/Latte/Essential/CoreExtension.php`
(`getFunctions()`) at every `v3.*` tag.

Functions are called in a Latte expression like PHP functions: `{first($items)}`.
Unlike filters, the set is small and changed only by addition. The 3.0.0 set is
identical to the 2.11 set, and nothing was added anywhere in the 3.1 line.


Core functions
--------------

| Function | 2.11 | 3.0 | 3.1 | Signature | Notes |
|---|---|---|---|---|---|
| `clamp` | yes | yes | yes | `(int\|float $value, int\|float $min, int\|float $max)` | also a filter |
| `divisibleBy` | yes | yes | yes | `(int $value, int $by)` | |
| `even` | yes | yes | yes | `(int $value)` | |
| `first` | yes | yes | yes | `(string\|iterable $value)` | also a filter |
| `group` | no | 3.0.16 | yes | `(iterable $values, string\|int\|Closure $by)` | also a filter |
| `hasBlock` | no | 3.0.10 | yes | `(string $name)` | receives the template implicitly |
| `hasTemplate` | no | 3.0.22 | yes | `(string $name)` | receives the template implicitly; resolves through the loader |
| `last` | yes | yes | yes | `(string\|iterable $value)` | also a filter |
| `odd` | yes | yes | yes | `(int $value)` | |
| `slice` | yes | yes | yes | `(string\|iterable $value, int $start, ?int $length = null, bool $preserveKeys = false)` | also a filter |


Functions from other packages
-----------------------------

Not provided by the engine. Read out of the bridge classes at
`nette/application` v3.3.0 and `nette/assets` v1.0.7 (see `README.md` for the
clone commands); availability depends on the other package's version, not on
the Latte version.

| Function | Provider | Registered in | Signature |
|---|---|---|---|
| `isLinkCurrent` | nette/application | `Bridges/ApplicationLatte/UIExtension::getFunctions()` | `(?string $destination = null, $args = [])` |
| `isModuleCurrent` | nette/application | same | `(string $moduleName)` |
| `asset` | nette/assets, 0.1.0+ | `Bridges/AssetsLatte/LatteExtension::getFunctions()` | `(string\|array\|Asset $reference, ...$options): Asset` |
| `tryAsset` | nette/assets, **0.2.0+** | same | `(string\|array\|Asset\|null $reference, ...$options): ?Asset` |

Both `nette/application` functions are registered **only when a presenter is
attached** (`UIExtension::getFunctions()` returns `[]` otherwise), so their
presence is not even constant within one project. That is another reason not to
report an unknown function.

`nette/assets` 0.1.0 also registered `assetWidth` and `assetHeight`; both were
gone by 0.2.0. They are listed here only so that a template containing them can
be recognised rather than flagged.


A note on how functions resolve
-------------------------------

In Latte 3 the `customFunctions` compiler pass rewrites a call whose name is a
registered Latte function into a runtime lookup, and leaves everything else as a
plain PHP call. The set of names that behave as Latte functions is therefore the
engine's registry plus whatever the project registered — the plugin cannot see
the latter. Reporting an unknown function as an error is not safe in either line.
