# eJournal

eJournal records and reviews a trader's market activity as positions assembled from executed transactions.

## Language

**Portfolio**:
A user-created journal boundary for related market activity. It may receive manual imports or be linked to a brokerage account, but it is not itself a brokerage account.
_Avoid_: Brokerage account, broker connection

**Transaction**:
An executed purchase or sale of a quantity of one symbol at one time and price. A partially filled Order may produce multiple Transactions.
_Avoid_: Trade, Order, Position

**Broker Fee**:
An amount posted by a broker that debits or credits cash because of market activity. It may concern one Transaction or an aggregate set of Transactions.
_Avoid_: Trading cost

**Fee Allocation**:
eJournal's attribution of an aggregate Broker Fee across eligible Transactions when the broker does not identify individual parents. It preserves the broker-posted total without claiming broker-reported per-Transaction amounts.
_Avoid_: Fee link, exact fee attribution

**Fee Date**:
The broker-provided calendar date that groups an aggregate Broker Fee. It may differ from a Position Date because the broker can use another accounting-day boundary.
_Avoid_: Position Date, local trade date

**Position**:
A continuous long or short exposure in one symbol, beginning when holdings move away from zero and ending when they return to zero. Scale-ins and partial exits remain part of the same position.
_Avoid_: Trade, round-trip trade

**Scratch Position**:
A closed Position whose direction-aware quantity-weighted Average Price Difference is exactly zero. It is neutral for outcome statistics while fee-inclusive Realized P&L remains unchanged.
_Avoid_: Scratch Trade

**Position Note**:
One user-authored, editable multiline note belonging to a position throughout its lifecycle.
_Avoid_: Remark, transaction note, broker description

**Tag**:
A user-defined label whose name, color, and identity belong to exactly one Portfolio. It can be applied only to Positions in that Portfolio.
_Avoid_: Global tag, cross-portfolio tag

**Position Chart**:
A historical view of market bars surrounding a Position, with its executed Transactions marked against price. It does not represent live market activity.
_Avoid_: Live chart

**Position Date**:
The New York exchange-local calendar date containing a US-stock day Position's opening and closing Transactions.
_Avoid_: Import date, UTC date

**Calendar P&L**:
The sum of fee-inclusive Realized P&L attributed to Position exit dates within a calendar period. It excludes unrealized P&L.
_Avoid_: Gross P&L, mark-to-market P&L, account balance change

**Calendar Week**:
A Monday-through-Sunday calendar period. Month boundaries do not split it.
_Avoid_: Sunday-first week, partial month week

**Weekly P&L**:
Calendar P&L for one complete Calendar Week, including dates outside the displayed month.
_Avoid_: Week-to-date P&L, visible-month subtotal

**Sub-minute Position**:
A Position whose elapsed time from its opening Transaction to its closing Transaction is less than 60 seconds. A Position held for exactly 60 seconds is not sub-minute.
_Avoid_: Sub-minute trade

**Average Price Difference**:
The signed per-share difference between a Position's quantity-weighted average sell price and quantity-weighted average buy price for its realized shares. It excludes fees; positive values indicate a favorable price move for either long or short Positions.
_Avoid_: Average spread

**10-Second Bar**:
An OHLCV summary for one wall-clock-aligned ten-second interval. It is the finest market-data interval shown for US-stock day Positions.
_Avoid_: 10-second chart, tick data
