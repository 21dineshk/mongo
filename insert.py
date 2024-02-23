from pymongo import MongoClient
import random
from datetime import datetime, timedelta
import string
import certifi

# MongoDB connection string - replace it with your actual connection string
connection_string = "your_mongodb_connection_string"
client = MongoClient("mongodb+srv://ddinesh:dinesh7981@cluster0.fm9aocc.mongodb.net/?retryWrites=true&w=majority", tlsCAFile=certifi.where())

# Specify your database and collection
db = client['weekly_deals']
collection = db['test_deals']

def random_string(length=6):
    """Generate a random string of fixed length."""
    letters = string.ascii_lowercase
    return ''.join(random.choice(letters) for i in range(length))

# Generate 1,000 unique householdIds
householdIds = ["".join(random.choices(string.ascii_uppercase + string.digits, k=7)) for _ in range(350)]

# Generate 10 unique storeIds
storeIds = ["".join(random.choices(string.ascii_uppercase + string.digits, k=7)) for _ in range(10)]

# Expanding to 10 distinct values for each field
categories = ["Canned Goods", "Fresh Produce", "Bakery", "Dairy", "Meat", "Seafood", "Frozen Foods", "Snacks", "Beverages", "Pharmacy"]
events = ["Fresh Pass Perk", "Seasonal Sale", "Weekly Deal", "Holiday Special", "Flash Sale", "Member Exclusive", "Clearance", "New Arrival", "Limited Time Offer", "Online Special"]
offerTypes = ["Personal Deal", "General Offer", "BOGO", "Discount", "Flash Offer", "Coupon", "Membership Offer", "Rebate", "Clearance Sale", "Online Exclusive"]

def create_document():
    """Create a single document."""
    return {
        "householdId": random.choice(householdIds),
        "storeId": random.choice(storeIds),
        "offerId": random_string(7),
        "rank": random.randint(1, 100),
        "category": random.choice(categories),
        "event": random.choice(events),
        "offerType": random.choice(offerTypes),
        "recentPurchase": datetime.now() - timedelta(days=random.randint(1, 365)),
        "aboutToExpire": datetime.now() + timedelta(days=random.randint(1, 365)),
        "newoffer": datetime.now() - timedelta(days=random.randint(1, 365))
    }

def insert_documents(n):
    """Insert n documents into the collection."""
    documents = [create_document() for _ in range(n)]
    collection.insert_many(documents)
    print(f"Inserted {n} documents.")

# Insert more than a million rows, ensuring the specified distribution
total_documents = 1000000 + 1  # Adjust the number as needed
insert_documents(total_documents)
