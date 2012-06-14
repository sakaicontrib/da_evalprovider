CREATE TABLE `HIER_PROVIDER_nodes` (
  `id` int(128) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `parent_id` int(128) DEFAULT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `HIER_PROVIDER_evalgrouprules` (
  `node_id` int(128) NOT NULL,
  `rule` varchar(255) DEFAULT NULL
);

INSERT INTO `HIER_PROVIDER_nodes`
(`name`, `parent_id`) VALUES
('root', 	null),
('School 1',	1),
('School 2',	1),
('Dept 1',	2),
('Dept 2',	2),
('Dept 3',	3),
('Dept 4',	3);

INSERT INTO `HIER_PROVIDER_evalgrouprules`
VALUES
(4,	'BUAD'),
(5,	'SPRING 2010'),
(6,	'DESN');