

# Create the policy 


PUT _plugins/_ism/policies/ssreport-policy-v4
{
  "policy": {
    "description": "A sample description of the policy",
    "default_state": "hot",
    "states": [
        {
            "name": "hot",
            "actions": [
                {
                    "retry": {
                        "count": 3,
                        "backoff": "exponential",
                        "delay": "1m"
                    },
                    "rollover": {
                        "min_doc_count": 2,
                        "copy_alias": false
                    }
                }
            ],
            "transitions": [
                {
                    "state_name": "warm"
                }
            ]
        },
        {
            "name": "warm",
            "actions": [],
            "transitions": [
                {
                    "state_name": "archive",
                    "conditions": {
                        "min_index_age": "30m"
                    }
                }
            ]
        },
        {
            "name": "archive",
            "actions": [
                {
                    "retry": {
                        "count": 3,
                        "backoff": "exponential",
                        "delay": "1m"
                    },
                    "delete": {}
                }
            ],
            "transitions": []
        }
    ],
    "ism_template": [
        {
            "index_patterns": [
                "ssreport-idx-*"
            ],
            "priority": 9999
        }
    ]
  }
}



# Create template
PUT _index_template/aa-report-template
{
  "index_patterns": [
    "ssreport-idx-*"
  ],
  "template": {
    "settings": {
      "index.number_of_shards": "1",
      "index.number_of_replicas": "1",
      "plugins.index_state_management.rollover_alias": "aa-report"
    },
    "mappings": {
      "properties": {
        "name": {
          "type": "keyword"
        },
        "age": {
          "type": "integer"
        }
      }
    }
  }
}


# bootstrap first index
PUT ssreport-idx-000001
{
  "aliases": {
    "aa-report": { 
      "is_write_index": true
    }
  }
}




# Test

## put some data
POST aa-report/_doc
{
  "name": "Jecica Alba",
  "age": 50
}



GET aa-report/_search
GET _cat/indices?v&s=index
