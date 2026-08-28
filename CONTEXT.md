# eJournal

eJournal records and reviews a trader's market activity as positions assembled from executed transactions.

## Language

**Position**:
A continuous long or short exposure in one symbol, beginning when holdings move away from zero and ending when they return to zero. Scale-ins and partial exits remain part of the same position.
_Avoid_: Trade, round-trip trade

**Position Note**:
One user-authored, editable multiline note belonging to a position throughout its lifecycle.
_Avoid_: Remark, transaction note, broker description

**Position Chart**:
A historical view of market bars surrounding a Position, with its executed Transactions marked against price. It does not represent live market activity.
_Avoid_: Live chart

**Position Date**:
The New York exchange-local calendar date containing a US-stock day Position's opening and closing Transactions.
_Avoid_: Import date, UTC date

**10-Second Bar**:
An OHLCV summary for one wall-clock-aligned ten-second interval. It is the finest market-data interval shown for US-stock day Positions.
_Avoid_: 10-second chart, tick data
