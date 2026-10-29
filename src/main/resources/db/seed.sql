-- 学科与知识点/题型分类初始化（幂等脚本，可重复执行）
-- 数学/语文/英语/物理/化学沿用原有分类，本次补齐生物、政治、历史、地理四科，
-- 并补充各科常见题型（七选五、短文改错、实验探究、工业流程、史料分析、地理图表等）。

-- 学科按名称判重：AI 自动归档时可能已按随机编码建过同名学科，避免出现两个「生物」
INSERT INTO t_subject (name, code, sort_no)
SELECT '数学', 'MATH', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM t_subject WHERE name = '数学');

INSERT INTO t_subject (name, code, sort_no)
SELECT '语文', 'CHINESE', 2 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM t_subject WHERE name = '语文');

INSERT INTO t_subject (name, code, sort_no)
SELECT '英语', 'ENGLISH', 3 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM t_subject WHERE name = '英语');

INSERT INTO t_subject (name, code, sort_no)
SELECT '物理', 'PHYSICS', 4 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM t_subject WHERE name = '物理');

INSERT INTO t_subject (name, code, sort_no)
SELECT '化学', 'CHEMISTRY', 5 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM t_subject WHERE name = '化学');

INSERT INTO t_subject (name, code, sort_no)
SELECT '生物', 'BIOLOGY', 6 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM t_subject WHERE name = '生物');

INSERT INTO t_subject (name, code, sort_no)
SELECT '政治', 'POLITICS', 7 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM t_subject WHERE name = '政治');

INSERT INTO t_subject (name, code, sort_no)
SELECT '历史', 'HISTORY', 8 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM t_subject WHERE name = '历史');

INSERT INTO t_subject (name, code, sort_no)
SELECT '地理', 'GEOGRAPHY', 9 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM t_subject WHERE name = '地理');

-- ---------- 数学 ----------
INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '集合与逻辑', '集合运算、命题与充要条件', 1 FROM t_subject s
WHERE s.name = '数学' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '集合与逻辑');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '函数与导数', '函数性质、导数几何意义与单调极值', 2 FROM t_subject s
WHERE s.name = '数学' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '函数与导数');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '三角函数', '图像与性质、三角恒等变换、解三角形', 3 FROM t_subject s
WHERE s.name = '数学' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '三角函数');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '数列', '等差等比数列、求和与递推', 4 FROM t_subject s
WHERE s.name = '数学' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '数列');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '立体几何', '空间线面关系、体积与空间向量', 5 FROM t_subject s
WHERE s.name = '数学' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '立体几何');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '解析几何', '直线与圆、圆锥曲线综合', 6 FROM t_subject s
WHERE s.name = '数学' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '解析几何');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '概率与统计', '古典概型、分布列、统计抽样与回归', 7 FROM t_subject s
WHERE s.name = '数学' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '概率与统计');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '平面向量', '向量运算、数量积与解三角形中的向量法', 8 FROM t_subject s
WHERE s.name = '数学' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '平面向量');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '不等式', '一元二次不等式、基本不等式与线性规划', 9 FROM t_subject s
WHERE s.name = '数学' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '不等式');

-- ---------- 语文 ----------
INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '现代文阅读', '信息类文本、文学类文本阅读', 1 FROM t_subject s
WHERE s.name = '语文' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '现代文阅读');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '文言文阅读', '实词虚词、断句与翻译', 2 FROM t_subject s
WHERE s.name = '语文' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '文言文阅读');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '古诗词鉴赏', '意象、手法与情感', 3 FROM t_subject s
WHERE s.name = '语文' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '古诗词鉴赏');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '作文', '审题立意、结构与素材', 4 FROM t_subject s
WHERE s.name = '语文' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '作文');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '名篇名句默写', '课内古诗文情境式默写', 5 FROM t_subject s
WHERE s.name = '语文' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '名篇名句默写');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '语言文字运用', '成语病句、衔接连贯、图文转换', 6 FROM t_subject s
WHERE s.name = '语文' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '语言文字运用');

-- ---------- 英语 ----------
INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '阅读理解', '细节理解、主旨大意、推理判断', 1 FROM t_subject s
WHERE s.name = '英语' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '阅读理解');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '完形填空', '语境理解与词义辨析', 2 FROM t_subject s
WHERE s.name = '英语' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '完形填空');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '语法填空', '时态、非谓语动词、从句', 3 FROM t_subject s
WHERE s.name = '英语' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '语法填空');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '书面表达', '应用文写作与读后续写', 4 FROM t_subject s
WHERE s.name = '英语' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '书面表达');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '七选五', '语篇衔接与逻辑关系还原', 5 FROM t_subject s
WHERE s.name = '英语' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '七选五');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '听力理解', '短对话、长对话与独白', 6 FROM t_subject s
WHERE s.name = '英语' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '听力理解');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '短文改错', '语法错误识别与修改', 7 FROM t_subject s
WHERE s.name = '英语' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '短文改错');

-- ---------- 物理 ----------
INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '运动与力', '牛顿定律、曲线运动与天体运动', 1 FROM t_subject s
WHERE s.name = '物理' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '运动与力');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '功与能', '动能定理、机械能守恒', 2 FROM t_subject s
WHERE s.name = '物理' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '功与能');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '电磁学', '电场、恒定电流、磁场与电磁感应', 3 FROM t_subject s
WHERE s.name = '物理' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '电磁学');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '实验题', '力学实验、电学实验、数据处理与误差分析', 4 FROM t_subject s
WHERE s.name = '物理' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '实验题');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '动量与碰撞', '动量定理、动量守恒与碰撞模型', 5 FROM t_subject s
WHERE s.name = '物理' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '动量与碰撞');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '热学', '分子动理论、气体实验定律、热力学定律', 6 FROM t_subject s
WHERE s.name = '物理' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '热学');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '光学与振动波', '几何光学、机械振动与机械波', 7 FROM t_subject s
WHERE s.name = '物理' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '光学与振动波');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '原子与近代物理', '光电效应、原子结构、核反应', 8 FROM t_subject s
WHERE s.name = '物理' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '原子与近代物理');

-- ---------- 化学 ----------
INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '化学基本概念', '物质分类、化学用语、阿伏加德罗常数', 1 FROM t_subject s
WHERE s.name = '化学' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '化学基本概念');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '化学反应原理', '速率平衡、电离水解、电化学', 2 FROM t_subject s
WHERE s.name = '化学' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '化学反应原理');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '元素与化合物', '常见元素及其化合物性质与转化', 3 FROM t_subject s
WHERE s.name = '化学' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '元素与化合物');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '有机化学', '官能团性质、同分异构与有机合成', 4 FROM t_subject s
WHERE s.name = '化学' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '有机化学');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '化学实验与探究', '实验装置、操作规范、现象分析与方案评价', 5 FROM t_subject s
WHERE s.name = '化学' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '化学实验与探究');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '工业流程题', '原料预处理、反应条件控制与产率计算', 6 FROM t_subject s
WHERE s.name = '化学' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '工业流程题');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '物质结构与性质', '原子结构、分子空间构型、晶体类型', 7 FROM t_subject s
WHERE s.name = '化学' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '物质结构与性质');

-- ---------- 生物 ----------
INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '分子与细胞', '细胞结构、代谢、增殖与分化', 1 FROM t_subject s
WHERE s.name = '生物' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '分子与细胞');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '遗传与进化', '孟德尔定律、基因表达、变异与进化', 2 FROM t_subject s
WHERE s.name = '生物' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '遗传与进化');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '稳态与调节', '神经调节、体液调节、免疫调节', 3 FROM t_subject s
WHERE s.name = '生物' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '稳态与调节');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '生物与环境', '种群、群落、生态系统与环境保护', 4 FROM t_subject s
WHERE s.name = '生物' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '生物与环境');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '生物技术与工程', '基因工程、细胞工程、发酵工程', 5 FROM t_subject s
WHERE s.name = '生物' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '生物技术与工程');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '实验设计与分析', '变量控制、对照设置、结果图表分析', 6 FROM t_subject s
WHERE s.name = '生物' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '实验设计与分析');

-- ---------- 政治 ----------
INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '经济与社会', '价格、生产经营、分配与社会保障', 1 FROM t_subject s
WHERE s.name = '政治' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '经济与社会');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '政治与法治', '党的领导、人民当家作主、依法治国', 2 FROM t_subject s
WHERE s.name = '政治' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '政治与法治');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '文化与哲学', '文化传承与创新、唯物论与辩证法', 3 FROM t_subject s
WHERE s.name = '政治' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '文化与哲学');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '当代国际政治与经济', '国家制度、国际组织与全球化', 4 FROM t_subject s
WHERE s.name = '政治' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '当代国际政治与经济');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '法律与生活', '民事权利、合同、婚姻家庭与纠纷解决', 5 FROM t_subject s
WHERE s.name = '政治' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '法律与生活');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '逻辑与思维', '概念判断、推理规则、辩证思维', 6 FROM t_subject s
WHERE s.name = '政治' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '逻辑与思维');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '时政热点分析', '材料信息提取与多模块综合作答', 7 FROM t_subject s
WHERE s.name = '政治' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '时政热点分析');

-- ---------- 历史 ----------
INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '中国古代史', '先秦至明清的政治、经济与文化', 1 FROM t_subject s
WHERE s.name = '历史' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '中国古代史');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '中国近代史', '晚清变局、辛亥革命、新民主主义革命', 2 FROM t_subject s
WHERE s.name = '历史' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '中国近代史');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '中国现代史', '新中国成立后的建设与改革开放', 3 FROM t_subject s
WHERE s.name = '历史' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '中国现代史');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '世界古代史', '古代文明与中古时期的世界', 4 FROM t_subject s
WHERE s.name = '历史' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '世界古代史');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '世界近代史', '文艺复兴、工业革命与殖民体系', 5 FROM t_subject s
WHERE s.name = '历史' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '世界近代史');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '世界现代史', '两次大战、冷战与全球化', 6 FROM t_subject s
WHERE s.name = '历史' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '世界现代史');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '史料实证与史学方法', '材料解读、史料辨析与论证', 7 FROM t_subject s
WHERE s.name = '历史' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '史料实证与史学方法');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '历史小论文', '观点提炼与史论结合写作', 8 FROM t_subject s
WHERE s.name = '历史' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '历史小论文');

-- ---------- 地理 ----------
INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '地球与地图', '经纬网、等高线、地球运动', 1 FROM t_subject s
WHERE s.name = '地理' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '地球与地图');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '大气与水', '大气受热、气候类型、水循环与洋流', 2 FROM t_subject s
WHERE s.name = '地理' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '大气与水');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '地貌与自然灾害', '外力作用、地貌类型、灾害防治', 3 FROM t_subject s
WHERE s.name = '地理' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '地貌与自然灾害');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '人口与城市', '人口迁移、城镇化与城市空间结构', 4 FROM t_subject s
WHERE s.name = '地理' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '人口与城市');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '农业与工业', '区位因素、产业转移与可持续发展', 5 FROM t_subject s
WHERE s.name = '地理' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '农业与工业');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '交通与区域发展', '交通布局、区域协调与国土整治', 6 FROM t_subject s
WHERE s.name = '地理' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '交通与区域发展');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '区域地理', '世界区域与中国分区特征', 7 FROM t_subject s
WHERE s.name = '地理' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '区域地理');

INSERT INTO t_category (subject_id, parent_id, name, description, sort_no)
SELECT s.id, 0, '地理图表判读', '等值线图、统计图与示意图分析', 8 FROM t_subject s
WHERE s.name = '地理' AND NOT EXISTS (SELECT 1 FROM t_category c WHERE c.subject_id = s.id AND c.parent_id = 0 AND c.name = '地理图表判读');
