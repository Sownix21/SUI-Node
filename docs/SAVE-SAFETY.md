# Optional management safeguards

Panel profiles offer a local read-only switch, disabled by default. When enabled, the
transport blocks POST, database restore and active latency probes before network I/O.
GET-based reading and backup download remain available. This is an app-side safety guard,
not a replacement for server-side token permissions.

App settings contain independently configurable pre-save review and interrupted-save
recovery, enabled by default. For APIv2 `save` requests, review fetches fresh records,
shows field changes with secrets masked, and requires confirmation before POST. It checks
again after review for intervening changes. Inbound initial-client selections are included.
Cancel sends nothing. Other operations such as restart/restore keep their own confirmations;
the field-diff sheet is not a preview engine for arbitrary commands.

Recovery durably writes encrypted hash-based evidence before sending. If the response is
lost, it uses GET to see whether the requested state is present, without retrying POST.
If that cannot be established, Pending changes blocks subsequent mutations for the panel.
The user can check again or explicitly acknowledge manual inspection before clearing the
local block. Clearing is not rollback. Turning the recovery option off does not erase an
existing unresolved receipt. Server-normalized fields can prevent automatic confirmation;
the conservative result is uncertainty, not an automatic duplicate creation.

APIv2 has no atomic compare-and-swap or transaction across panels. Another administrator
can race the final POST, and a GET match proves state, not who caused it. Back up important
configurations and inspect uncertain operations. This does not make writes infallible.

Profiles also support optional groups, favorites and provider memos. Metadata-only edits
do not require the panel to be reachable; connection changes still require testing.
Optional encrypted offline overviews are for inspection only. None of these local profile
options writes to the panel. Original panel backup bytes remain unchanged.
