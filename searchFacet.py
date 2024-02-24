from pymongo import MongoClient

# Replace these with your actual connection details
MONGO_URI = 'your_mongodb_connection_string'
DB_NAME = 'your_database_name'
COLLECTION_NAME = 'your_collection_name'

client = MongoClient(MONGO_URI)
db = client[DB_NAME]
collection = db[COLLECTION_NAME]

# Define the aggregation pipeline
pipeline = [
  {
    '$search': {
      'index': 'yourSearchIndexName',  # Replace with your Atlas Search index name
      'compound': {
        'filter': [
          {
            'text': {
              'query': '12B3123',  # Your householdId value
              'path': 'householdId',
            },
          },
          {
            'text': {
              'query': '999999A',  # Your storeId value
              'path': 'storeId',
            },
          },
        ],
      },
    },
  },
  {
    '$facet': {
      "categoriesCount": [
        {
          '$group': {
            '_id': "$category",
            'count': {'$sum': 1}
          }
        }
      ]
    }
  }
]

# Execute the aggregation query
result = collection.aggregate(pipeline)

# Print the results
for doc in result:
    print(doc)
