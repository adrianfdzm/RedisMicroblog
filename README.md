# RedisMicroblog
###Redis practical example for academic purposes

This is a simple implementation of a Java console microblogging app in order to decipt how some of the Redis Data types work. Jedis client is used. The application allows the user to:

 1. Create new user
 2. Log in as a user
 3. Create new posts
 4. Show general timeline
 5. Show user timeline
 6. Follow a user
 7. Show followers in common with a user
 
Users are stored as hashes. An autoincrement numerical key is created using Redis `INCR` operation in order to generate user ids. An additional hash is used to map user names with user ids

Another autoincrement numerical key is created using `INCR` operation to generate posts ids. Posts are also stored as hashes. When a new post is created it is added to a timeline reids list (by its id). Users that follow the creator of the post get their own timeline updated. Each user has a redis list (`'posts:' + userId`) which contains all the post of his timeline

User followers are stored in a sorted set using the timestamp as score. To know the followers in common of two given users we use the redis `ZINTERSTORE` operation




