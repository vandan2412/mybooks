
Android Accounting App — Complete Party, Sales, Purchase and Ledger System

This project is an Android accounting application built using Java and Firebase Firestore.
It manages parties, transactions, and automatically generates a ledger from journal entries.

The system supports common business accounting operations including:
Sales
Purchase
Sales Return
Purchase Return
Payment
Receipt
Opening Balance
Party Ledger

The accounting rule used throughout the system:
If selected party appears in "to" field → Debit entry
If selected party appears in "from" field → Credit entry

Application Modules

Party Management
Stores and manages all customer and supplier information.

Each party record contains:
Party Name
Group (Customer / Supplier / Other)
City
Phone
Alternate Phone
Opening Balance
Current Balance

Journal Entry System
All financial transactions are stored in one collection called Journal.
Every transaction such as sales, purchase, payment, receipt, or return is recorded here.
Ledger is generated from these journal records automatically.

Supported Transaction Types

Sales
Goods sold to a party
Party becomes debtor
Entry effect:
Party → Debit
Sales Account → Credit

Purchase
Goods purchased from a party
Party becomes creditor
Entry effect:
Purchase Account → Debit
Party → Credit

Sales Return
Customer returns goods
Entry effect:
Sales Return Account → Debit
Party → Credit

Purchase Return
Goods returned to supplier
Entry effect:
Party → Debit
Purchase Return Account → Credit

Payment
Money paid to party
Entry effect:
Party → Debit
Cash/Bank → Credit

Receipt
Money received from party
Entry effect:
Cash/Bank → Debit
Party → Credit

Firestore Database Structure

Collection name: Party
Each document contains:
partyName : String
group : String
city : String
phone : String
altPhone : String
openingBalance : Number
currentBalance : Number

Collection name: Journal
Each document contains:
amount : Number
date : String
from : String
to : String
invoiceNo : String
remarks : String
type : String

Example Journal Record
amount = 500
date = 02-12-2025
from = Purchases Account
to = dawn
invoiceNo = P-1
remarks = Purchase Entry for P-1
type = Purchase

User Interface Flow

Screen 1: Party List Screen
Displays all parties stored in database.

UI contains:
Heading showing Party Ledger
Search bar
Table heading row showing Name, Group, Balance
ListView of parties
Back button at bottom

Search supports:
Party name
Group name
Balance
Real-time filtering
Case-insensitive search

When user taps a party → ledger screen opens.

Screen 2: Party Ledger Screen
Displays ledger of selected party.

UI contains:
Heading showing selected party name
ListView showing ledger entries

Each ledger row displays:
Date
Invoice Number
Remarks
Debit amount
Credit amount

Ledger Generation Logic

Step 1
Receive selected party name from Party List screen.

Step 2
Fetch journal entries where:
from equals selected party
to equals selected party

Step 3
Convert entries into ledger format:

If party equals "to" field → Debit
If party equals "from" field → Credit

Step 4
Display entries in ledger list.

Data Processing Logic

Party List Screen
Fetch all Party documents from Firestore
Store in memory list
Apply search filter
Display filtered list

Ledger Screen
Receive party name
Fetch matching journal entries
Classify debit and credit
Display ledger

Technology Used

Android Studio
Java
Firebase Firestore
XML UI Layout
ListView with Adapter pattern

Project Components

Activities
PartyList.java
Displays all parties and search

PartyLedger.java
Displays ledger of selected party

Models
PartyModel
Represents party data

LedgerModel
Represents ledger row

Adapters
PartyAdapter
Binds party data to ListView

LedgerAdapter
Binds ledger data to ListView

Layout Files
activity_party_list.xml
party_row.xml
activity_party_ledger.xml
ledger_row.xml

How Financial Data Flows in App

Transaction created (Sales, Purchase, Payment, etc.)
Transaction stored in Journal collection
Party balance updated
Ledger automatically reflects change
User views ledger anytime

Firebase Setup

Create Firebase project
Add Android app
Enable Firestore Database
Add google-services.json to project
Create collections:
Party
Journal
Insert sample records

How to Run Project

Clone repository
Open project in Android Studio
Connect Firebase
Sync Gradle
Insert sample data
Run app




