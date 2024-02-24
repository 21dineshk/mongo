from pymongo import MongoClient

# Replace the following with your actual connection URI
mongo_uri = "your_mongodb_connection_uri"
client = MongoClient(mongo_uri)

# Replace 'your_database_name' and 'your_collection_name' with your actual database and collection names
db = client['your_database_name']
collection = db['your_collection_name']

# Define the aggregation pipeline
pipeline = [
    {
        "$searchMeta": {
            "index": 'yourSearchIndexName', # Specify your Atlas Search index name
            "compound": {
                "filter": [ # Use filter clauses for householdId and storeId
                    {
                        "equals": {
                            "path": 'householdId',
                            "value": '12B3123'
                        }
                    },
                    {
                        "equals": {
                            "path": 'storeId',
                            "value": '999999A'
                        }
                    }
                ]
            },
            "facet": { # Define your facets
                "operator": {
                    "type": 'facet',
                    "facets": {
                        "categoriesCount": { # Name your facet
                            "type": 'terms',
                            "path": 'category' # Facet on the category field
                        }
                    }
                }
            }
        }
    }
]

# Execute the aggregation pipeline
result = collection.aggregate(pipeline)

# Print the results
for doc in result:
    print(doc)
