# Commercial Expiry Service

This Play app produces a Kinesis stream of content items whose commercial expiry status has changed recently. 
Each entry in the stream consists of a content ID (the content's path) as the key and a boolean expiry status as the value.

The application is configured from config files in the `guconf-flexible` s3 bucket in the gu-aws-composer aws account. To successfully
run the app locally you will need to ensure that your gu-composer aws credentials are picked up by the default credential provider chain 
(e.g by exposing them as environment variables before running sbt), this is left as an exercise for the developer.

