from pymongo import MongoClient
import random
from datetime import datetime, timedelta
import itertools
import string

# MongoDB connection string - replace it with your actual connection string
connection_string = "your_mongodb_connection_string"
client = MongoClient(connection_string)

# Specify your database and collection
db = client['weekly_deals']
collection = db['deals']

# Generate 3,500 unique householdIds
householdIds = ["".join(random.choices(string.ascii_uppercase + string.digits, k=7)) for _ in range(3500)]

# Generate 10 unique storeIds
storeIds = ["".join(random.choices(string.ascii_uppercase + string.digits, k=7)) for _ in range(10)]

# Categories, events, and offerTypes
categories = ["Canned Goods", "Fresh Produce", "Bakery", "Dairy", "Meat", "Seafood", "Frozen Foods", "Snacks", "Beverages", "Pharmacy"]
events = ["Fresh Pass Perk", "Seasonal Sale", "Weekly Deal", "Holiday Special", "Flash Sale", "Member Exclusive", "Clearance", "New Arrival", "Limited Time Offer", "Online Special"]
offerTypes = ["Personal Deal", "General Offer", "BOGO", "Discount", "Flash Offer", "Coupon", "Membership Offer", "Rebate", "Clearance Sale", "Online Exclusive"]

def create_documents_for_household(householdId, num_docs=2850):
    """Create a specified number of documents for a given householdId."""
    for rank in range(1, num_docs + 1):
        yield {
            "householdId": householdId,
            "storeId": random.choice(storeIds),
            "offerId": ''.join(random.choices(string.ascii_lowercase + string.digits, k=7)),
            "rank": rank,
            "category": random.choice(categories),
            "event": random.choice(events),
            "offerType": random.choice(offerTypes),
            "recentPurchase": datetime.now() - timedelta(days=random.randint(1, 365)),
            "aboutToExpire": datetime.now() + timedelta(days=random.randint(1, 365)),
            "newoffer": datetime.now() - timedelta(days=random.randint(1, 365))
        }

def insert_documents():
    """Insert the documents into the collection."""
    for householdId in householdIds:
        documents = create_documents_for_household(householdId)
        collection.insert_many(documents)
        print(f"Inserted documents for householdId {householdId}")

insert_documents()
