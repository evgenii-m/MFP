insert into user_data (id, external_id)
values (101, 201);

insert into music_collection (id, last_scan_time, title, external_id, type, is_private, is_synchronized, removed)
values (1001, '2020-11-21 00:00:00.000', 'Test collection', '24412771', 'RAINDROPS', true, true, false);

insert into user_collection (user_id, collection_id, is_owner, can_write, selected)
values (101, 1001, true, true, true);