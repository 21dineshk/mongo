from pymongo import MongoClient
import time
import certifi

# Connect to MongoDB
client = MongoClient("mongodb+srv://ddinesh:dinesh7981@cluster0.fm9aocc.mongodb.net/?retryWrites=true&w=majority", tlsCAFile=certifi.where())
db = client['weekly_deals']
collection = db['test_deals']

# Ensure you replace 'someHouseholdId' with an actual householdId from your dataset
household_id = 'BL65FJ3'

def query_without_index():
    start_time = time.time()
    li = list(collection.find({"householdId": household_id}))
    print(len(li))
    end_time = time.time()
    print(f"Time taken without index: {end_time - start_time} seconds")

query_without_index()

def query_with_atlas_search_index():
    start_time = time.time()
    # Construct an aggregation pipeline using the $search stage
    pipeline = [
        {
            "$search": {
                "index": "default",
                "text": {
                    "query": household_id,
                    "path": "householdId"  # Adjust if your search index is configured differently
                }
            }
        }
    ]
    results = list(collection.aggregate(pipeline))
    print(len(results))
    end_time = time.time()
    print(f"Time taken with Atlas Search index: {end_time - start_time} seconds")

# Run the Atlas Search query
query_with_atlas_search_index()
