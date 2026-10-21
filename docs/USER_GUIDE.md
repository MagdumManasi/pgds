# User guide

| Role | Typical tasks |
|---|---|
| Super Admin | Create staff accounts, manage master data, everything an officer can do. |
| Government Officer | Watch the dashboard; register ration cards and members; assign cards to shops; triage complaints; review AI alerts; read forecasts and reports. |
| Warehouse Manager | Record stock received, transfers to shops, damage. Stock is never edited, only added to the ledger. |
| FPS Dealer | Enter a card number, check the remaining entitlement, issue grain, print the receipt, report damaged stock. |
| Beneficiary | See card, family, remaining entitlement, receipts, nearby shops; submit and track complaints. |
| Auditor | Read-only access to AI alerts and reports. |

## Common flows
**Onboard a family**: beneficiary registers an account -> officer creates the ration card (category, members, shop) -> officer adds members and enters the
beneficiary's username in "Link login" -> the beneficiary sees their card.

**Distribute**: dealer enters the card number -> "Check entitlement" shows what is left this month -> adjust quantities -> "Issue grain" -> print receipt.
The system refuses ineligible cards, cards of another shop, quantities above the monthly limit, and quantities above the shop's stock.

**Replenish a shop**: warehouse manager -> Transfer to shop. Both ledgers update together; the shop sees the stock immediately.

**Handle an alert**: AI Alerts -> Review (under review) -> Resolve or Dismiss with a note. Alerts are never deleted.

## Account safety
Passwords are stored hashed (BCrypt). Five wrong passwords lock the account for 15 minutes. Change your password under your name -> My account.
