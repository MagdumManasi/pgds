# Complaint handling guideline

Project guideline for how complaints are classified and handled.

## Categories and routing
Every complaint is classified automatically. Overcharging, denial of ration and short weight complaints get high priority. Poor quality and shop closed complaints get medium priority. Card issues get low priority. Overcharging and denial of ration go to the District Supply Office. Short weight goes to the Legal Metrology Department. Poor quality goes to the Quality Control Cell. Shop closed goes to the Shop Licensing Cell. Card issues go to the Card Section. Anything else goes to the District Supply Office.

## How classification works
A machine learning model trained on example complaints chooses the category. If the model is not confident, keyword rules decide. Officers can always override the category, priority and department when they review a complaint.

## Officer workflow
A complaint starts as Open. The officer moves it to In progress while investigating, then to Resolved or Rejected, and adds a resolution note that the citizen can read. As a guideline, high priority complaints should be acknowledged first.

## What a citizen should include
Describe what happened, when, and at which shop. Choose the related shop if the portal offers it. Do not include Aadhaar numbers or passwords in the complaint text.
