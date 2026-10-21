# PGDS operating rules

How the GrainGuard AI system applies the rules. These are the rules the software enforces.

## Monthly entitlement calculation
Entitlement is calculated from the ration card category and family size. An AAY card receives 35 kg per month for the whole family. A PHH card receives 5 kg per person per month. The total is split into rice and wheat using the configured rice share, which is 50 percent by default. For example, a PHH card with 4 members receives 20 kg in total, which is 10 kg rice and 10 kg wheat.

## Checks before grain is issued
The system refuses a distribution if the ration card is unknown, not eligible, or assigned to a different shop. It also refuses if the quantity is more than the entitlement still remaining for the current month, or more than the stock the shop holds. The same grain cannot appear twice in one request. When all checks pass, the system saves the distribution, reduces shop stock, and generates a receipt number.

## Stock ledger
Stock is never edited directly. Every movement is a ledger entry: opening stock, receipt, transfer in, transfer out, distribution, or damage. The balance is the sum of these entries. Opening, receipt and transfer in add stock. Transfer out, distribution and damage reduce it. This gives a complete audit trail showing who changed stock and when.

## Transfers and damage
A warehouse manager can receive stock, report damage, and transfer stock to the shops served by that warehouse. A transfer writes a transfer-out entry at the warehouse and a transfer-in entry at the shop together. A transfer or damage report larger than the available balance is refused. A shop dealer can report damaged stock at their own shop.

## Roles and access
Super Admin manages accounts and master data. Government Officer manages ration cards, members, complaints, alerts, forecasts and reports. Warehouse Manager works only on their own warehouse. FPS Dealer works only on their own shop. Beneficiary sees only their own card, entitlement, receipts and complaints. Auditor has read-only access to alerts and reports.

## Account security
Passwords are stored hashed. After five wrong passwords an account is locked for 15 minutes. Any user can change their password from the My account page.
