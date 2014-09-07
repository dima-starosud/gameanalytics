with unique_users as (
  select user_id
    from events
   where time_stamp between 1234 and 3452345
   group by user_id)
select count(null)
  from unique_users
