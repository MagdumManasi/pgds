# AI alerts guide

How to read and handle the alerts raised by the AI service.

## Alert types
Anomaly alerts flag unusual distributions. Low stock alerts warn that a shop or warehouse may run out of a grain soon. Demand shortfall alerts warn that predicted demand for next month is higher than current stock.

## How anomalies are detected
Two methods are combined. An Isolation Forest model scores each distribution using four features: quantity compared with entitlement, how many times the card was served this month, the hour of the day, and how many distributions the shop made in that hour. A score of 0.65 or more is flagged, and 0.75 or more is high severity. Two fixed rules always apply: a distribution outside 06:00 to 22:00 is flagged, and 25 or more distributions by one shop within an hour is flagged as a burst.

## Stock exhaustion levels
Days left is the current stock divided by the average daily outflow over the last 30 days. Critical means 3 days or less, warning means 7 days or less, and watch means 14 days or less.

## How to review an alert
Open the alert and read the details. Check the shop, time and card. Move it to Under review, investigate, then Resolve it or Dismiss it as a false alarm and write a short note. Alerts are never deleted, so the history stays available for audit. Running the analysis again does not create duplicate alerts.

## Limits of the models
Alerts are advice for humans, not proof of fraud. They never block a distribution. The models need several months of real data before their results are reliable.
