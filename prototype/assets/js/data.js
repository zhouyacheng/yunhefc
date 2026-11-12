(function () {
  const now = new Date();

  function iso(offsetDays, hour, minute) {
    const d = new Date(now);
    d.setDate(d.getDate() + offsetDays);
    d.setHours(hour == null ? 20 : hour, minute == null ? 0 : minute, 0, 0);
    return d.toISOString();
  }

  const players = [
    { id: 1, name: '管理员', jersey: 10, position: 'MIDFIELDER', secondary: ['FORWARD'], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 4, appearances: 18, goals: 6, assists: 8, color: '#2563eb', admin: true, wechat: true },
    { id: 2, name: '林外援', jersey: 27, position: 'MIDFIELDER', secondary: ['FORWARD'], region: '苏州', type: 'GUEST', status: 'ACTIVE', skill: 3, appearances: 4, goals: 1, assists: 2, color: '#0f766e' },
    { id: 3, name: '陈子昂', jersey: 8, position: 'MIDFIELDER', secondary: ['DEFENDER'], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 4, appearances: 17, goals: 4, assists: 6, color: '#7c3aed', wechat: true },
    { id: 4, name: '王浩', jersey: 1, position: 'GOALKEEPER', secondary: [], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 4, appearances: 16, goals: 0, assists: 0, color: '#b45309', wechat: true },
    { id: 5, name: '李岩', jersey: 4, position: 'DEFENDER', secondary: ['MIDFIELDER'], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 4, appearances: 18, goals: 0, assists: 1, color: '#be123c' },
    { id: 6, name: '赵鹏', jersey: 5, position: 'DEFENDER', secondary: [], region: '南通', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 15, goals: 1, assists: 0, color: '#0369a1' },
    { id: 7, name: '孙磊', jersey: 2, position: 'DEFENDER', secondary: ['MIDFIELDER'], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 12, goals: 0, assists: 2, color: '#4d7c0f' },
    { id: 8, name: '周凯', jersey: 6, position: 'MIDFIELDER', secondary: ['DEFENDER'], region: '杭州', type: 'PLAYER', status: 'ACTIVE', skill: 4, appearances: 17, goals: 2, assists: 7, color: '#a21caf' },
    { id: 9, name: '吴迪', jersey: 7, position: 'MIDFIELDER', secondary: ['FORWARD'], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 14, goals: 3, assists: 3, color: '#0e7490' },
    { id: 10, name: '郑宇', jersey: 9, position: 'FORWARD', secondary: ['MIDFIELDER'], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 5, appearances: 18, goals: 14, assists: 4, color: '#c2410c' },
    { id: 11, name: '冯晨', jersey: 11, position: 'FORWARD', secondary: [], region: '昆山', type: 'PLAYER', status: 'ACTIVE', skill: 4, appearances: 16, goals: 9, assists: 2, color: '#15803d' },
    { id: 12, name: '蒋涛', jersey: 12, position: 'GOALKEEPER', secondary: [], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 6, goals: 0, assists: 0, color: '#9333ea' },
    { id: 13, name: '韩旭', jersey: 13, position: 'DEFENDER', secondary: ['FORWARD'], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 10, goals: 0, assists: 1, color: '#1d4ed8' },
    { id: 14, name: '曹阳', jersey: 14, position: 'MIDFIELDER', secondary: [], region: '嘉兴', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 9, goals: 1, assists: 2, color: '#b91c1c' },
    { id: 15, name: '许峰', jersey: 15, position: 'FORWARD', secondary: ['MIDFIELDER'], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 2, appearances: 7, goals: 2, assists: 0, color: '#047857' },
    { id: 16, name: '何伟', jersey: 16, position: 'DEFENDER', secondary: [], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 2, appearances: 5, goals: 0, assists: 0, color: '#6d28d9' },
    { id: 17, name: '高翔', jersey: 17, position: 'MIDFIELDER', secondary: ['FORWARD'], region: '宁波', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 8, goals: 2, assists: 1, color: '#0f766e' },
    { id: 18, name: '罗宾', jersey: 18, position: 'FORWARD', secondary: [], region: '上海', type: 'GUEST', status: 'ACTIVE', skill: 3, appearances: 3, goals: 2, assists: 0, color: '#ea580c' },
    { id: 19, name: '马超', jersey: 19, position: 'GOALKEEPER', secondary: [], region: '上海', type: 'GUEST', status: 'ACTIVE', skill: 3, appearances: 2, goals: 0, assists: 0, color: '#0e7490' },
    { id: 20, name: '邓超', jersey: 20, position: 'DEFENDER', secondary: ['MIDFIELDER'], region: '上海', type: 'GUEST', status: 'ACTIVE', skill: 2, appearances: 1, goals: 0, assists: 0, color: '#7c2d12' },
    { id: 21, name: '待审核球员', jersey: 30, position: 'MIDFIELDER', secondary: [], region: '上海', type: 'GUEST', status: 'PENDING', skill: 3, appearances: 0, goals: 0, assists: 0, color: '#64748b' },
    { id: 22, name: '沈拓', jersey: 41, position: 'GOALKEEPER', secondary: [], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 6, goals: 0, assists: 0, color: '#1e40af' },
    { id: 23, name: '程野', jersey: 42, position: 'DEFENDER', secondary: ['MIDFIELDER'], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 9, goals: 0, assists: 1, color: '#166534' },
    { id: 24, name: '陆鸣', jersey: 43, position: 'DEFENDER', secondary: [], region: '苏州', type: 'PLAYER', status: 'ACTIVE', skill: 4, appearances: 12, goals: 1, assists: 0, color: '#9a3412' },
    { id: 25, name: '方锐', jersey: 44, position: 'DEFENDER', secondary: ['FORWARD'], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 2, appearances: 4, goals: 0, assists: 0, color: '#701a75' },
    { id: 26, name: '袁朗', jersey: 45, position: 'MIDFIELDER', secondary: ['FORWARD'], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 4, appearances: 13, goals: 5, assists: 6, color: '#0c4a6e' },
    { id: 27, name: '钟毅', jersey: 46, position: 'MIDFIELDER', secondary: ['DEFENDER'], region: '杭州', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 11, goals: 2, assists: 4, color: '#7e22ce' },
    { id: 28, name: '姚远', jersey: 47, position: 'MIDFIELDER', secondary: [], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 8, goals: 1, assists: 3, color: '#065f46' },
    { id: 29, name: '唐骁', jersey: 48, position: 'FORWARD', secondary: ['MIDFIELDER'], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 4, appearances: 10, goals: 8, assists: 2, color: '#b45309' },
    { id: 30, name: '薛航', jersey: 49, position: 'FORWARD', secondary: [], region: '昆山', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 7, goals: 4, assists: 1, color: '#be185d' },
    { id: 31, name: '严铮', jersey: 50, position: 'FORWARD', secondary: ['MIDFIELDER'], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 2, appearances: 3, goals: 1, assists: 0, color: '#374151' },
    { id: 32, name: '顾言', jersey: 51, position: 'GOALKEEPER', secondary: [], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 5, goals: 0, assists: 0, color: '#075985' },
    { id: 33, name: '邵杰', jersey: 52, position: 'DEFENDER', secondary: [], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 8, goals: 0, assists: 1, color: '#3f6212' },
    { id: 34, name: '崔宁', jersey: 53, position: 'DEFENDER', secondary: ['MIDFIELDER'], region: '苏州', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 11, goals: 1, assists: 0, color: '#831843' },
    { id: 35, name: '潘越', jersey: 54, position: 'DEFENDER', secondary: [], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 2, appearances: 6, goals: 0, assists: 0, color: '#365314' },
    { id: 36, name: '范哲', jersey: 55, position: 'MIDFIELDER', secondary: ['FORWARD'], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 4, appearances: 13, goals: 3, assists: 5, color: '#1e3a8a' },
    { id: 37, name: '金诚', jersey: 56, position: 'MIDFIELDER', secondary: ['DEFENDER'], region: '杭州', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 9, goals: 1, assists: 4, color: '#9d174d' },
    { id: 38, name: '石磊', jersey: 57, position: 'MIDFIELDER', secondary: [], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 7, goals: 2, assists: 2, color: '#115e59' },
    { id: 39, name: '廖凡', jersey: 58, position: 'FORWARD', secondary: ['MIDFIELDER'], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 4, appearances: 12, goals: 9, assists: 3, color: '#9a3412' },
    { id: 40, name: '谭凯', jersey: 59, position: 'FORWARD', secondary: [], region: '昆山', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 8, goals: 5, assists: 1, color: '#5b21b6' },
    { id: 41, name: '龚宇', jersey: 60, position: 'GOALKEEPER', secondary: [], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 2, appearances: 3, goals: 0, assists: 0, color: '#0f766e' },
    { id: 42, name: '邱晨', jersey: 61, position: 'DEFENDER', secondary: [], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 10, goals: 0, assists: 2, color: '#7c2d12' },
    { id: 43, name: '侯亮', jersey: 62, position: 'DEFENDER', secondary: ['MIDFIELDER'], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 4, appearances: 14, goals: 1, assists: 1, color: '#14532d' },
    { id: 44, name: '戴维', jersey: 63, position: 'DEFENDER', secondary: [], region: '南通', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 9, goals: 0, assists: 0, color: '#7e22ce' },
    { id: 45, name: '秦峰', jersey: 64, position: 'MIDFIELDER', secondary: ['FORWARD'], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 4, appearances: 15, goals: 4, assists: 6, color: '#b91c1c' },
    { id: 46, name: '江涛', jersey: 65, position: 'MIDFIELDER', secondary: [], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 8, goals: 1, assists: 3, color: '#0e7490' },
    { id: 47, name: '尹航', jersey: 66, position: 'MIDFIELDER', secondary: ['DEFENDER'], region: '嘉兴', type: 'PLAYER', status: 'ACTIVE', skill: 2, appearances: 5, goals: 0, assists: 2, color: '#a16207' },
    { id: 48, name: '龙飞', jersey: 67, position: 'FORWARD', secondary: ['MIDFIELDER'], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 5, appearances: 16, goals: 12, assists: 4, color: '#be123c' },
    { id: 49, name: '万鹏', jersey: 68, position: 'FORWARD', secondary: [], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 7, goals: 3, assists: 1, color: '#4d7c0f' },
    { id: 50, name: '宋阳', jersey: 69, position: 'GOALKEEPER', secondary: [], region: '上海', type: 'PLAYER', status: 'ACTIVE', skill: 3, appearances: 4, goals: 0, assists: 0, color: '#4338ca' },
    { id: 51, name: '黎川', jersey: 70, position: 'GOALKEEPER', secondary: [], region: '宁波', type: 'PLAYER', status: 'ACTIVE', skill: 2, appearances: 2, goals: 0, assists: 0, color: '#78716c' }
  ];

  function reg(playerId, type, status, minutesAgo) {
    return { playerId, type, status, at: new Date(now.getTime() - (minutesAgo || 0) * 60000).toISOString() };
  }

  const matches = [
    {
      id: 'm1',
      seasonMatchNo: 57,
      totalMatchNo: 200,
      title: '云河FC vs 海港联',
      type: 'OFFICIAL',
      format: '8人制',
      start: iso(3, 20, 0),
      end: iso(3, 22, 0),
      signupDeadline: iso(3, 0, 0),
      cancelLock: iso(3, 10, 0),
      venue: '浦东足球公园 3 号场',
      opponent: '海港联',
      ourSide: 'HOME',
      competition: '城市业余联赛',
      season: '2026',
      round: 9,
      status: 'SIGNUP_OPEN',
      capacity: 10,
      guestCapacity: 1,
      waitlistCapacity: 2,
      includeGuests: true,
      score: { home: null, away: null },
      registrations: [
        reg(1, 'PLAYER', 'CONFIRMED', 900), reg(3, 'PLAYER', 'CONFIRMED', 860),
        reg(4, 'PLAYER', 'CONFIRMED', 820), reg(5, 'PLAYER', 'CONFIRMED', 780),
        reg(6, 'PLAYER', 'CONFIRMED', 740), reg(7, 'PLAYER', 'CONFIRMED', 700),
        reg(8, 'PLAYER', 'CONFIRMED', 660), reg(9, 'PLAYER', 'CONFIRMED', 620),
        reg(10, 'PLAYER', 'CONFIRMED', 580), reg(11, 'PLAYER', 'CONFIRMED', 540),
        reg(12, 'PLAYER', 'WAITLISTED', 500), reg(13, 'PLAYER', 'CANCELLED', 460),
        reg(2, 'GUEST', 'CONFIRMED', 420), reg(18, 'GUEST', 'CANCELLED', 380)
      ],
      attendance: [],
      events: [],
      lineups: {
        MAIN: {
          formationId: '232',
          starters: { gk: 4, d1: 5, d2: 6, m1: 1, m2: 3, m3: 8, f1: 10, f2: 11 },
          bench: [7, 9]
        }
      },
      grouping: null
    },
    {
      id: 'm2',
      seasonMatchNo: 59,
      totalMatchNo: 202,
      title: '周中训练',
      type: 'TRAINING',
      format: '8人制',
      start: iso(9, 19, 30),
      end: iso(9, 21, 30),
      signupDeadline: iso(9, 0, 0),
      cancelLock: iso(9, 10, 0),
      venue: '张江体育中心',
      opponent: '',
      ourSide: null,
      competition: '',
      season: '',
      round: null,
      status: 'SIGNUP_OPEN',
      capacity: 30,
      guestCapacity: 0,
      waitlistCapacity: 2,
      includeGuests: false,
      score: { home: null, away: null },
      registrations: [
        reg(1, 'PLAYER', 'CONFIRMED', 1200), reg(3, 'PLAYER', 'CONFIRMED', 1100),
        reg(4, 'PLAYER', 'CONFIRMED', 1000), reg(5, 'PLAYER', 'CONFIRMED', 900),
        reg(6, 'PLAYER', 'CONFIRMED', 800), reg(7, 'PLAYER', 'CONFIRMED', 700),
        reg(8, 'PLAYER', 'CONFIRMED', 600), reg(9, 'PLAYER', 'CONFIRMED', 500),
        reg(22, 'PLAYER', 'CONFIRMED', 480), reg(23, 'PLAYER', 'CONFIRMED', 460),
        reg(24, 'PLAYER', 'CONFIRMED', 440), reg(25, 'PLAYER', 'CONFIRMED', 420),
        reg(26, 'PLAYER', 'CONFIRMED', 400), reg(27, 'PLAYER', 'CONFIRMED', 380),
        reg(28, 'PLAYER', 'CONFIRMED', 360), reg(29, 'PLAYER', 'CONFIRMED', 340),
        reg(30, 'PLAYER', 'CONFIRMED', 320), reg(31, 'PLAYER', 'CONFIRMED', 300),
        reg(32, 'PLAYER', 'CONFIRMED', 280), reg(33, 'PLAYER', 'CONFIRMED', 260),
        reg(34, 'PLAYER', 'CONFIRMED', 240), reg(35, 'PLAYER', 'CONFIRMED', 220),
        reg(36, 'PLAYER', 'CONFIRMED', 200), reg(37, 'PLAYER', 'CONFIRMED', 180),
        reg(38, 'PLAYER', 'CONFIRMED', 160), reg(39, 'PLAYER', 'CONFIRMED', 140),
        reg(40, 'PLAYER', 'CONFIRMED', 120), reg(41, 'PLAYER', 'CONFIRMED', 100)
      ],
      attendance: [],
      events: [],
      lineups: {},
      grouping: null
    },
    {
      id: 'm3',
      seasonMatchNo: 56,
      totalMatchNo: 199,
      title: '云河FC vs 东岸竞技',
      type: 'OFFICIAL',
      format: '8人制',
      start: iso(-3, 20, 0),
      end: iso(-3, 22, 0),
      signupDeadline: iso(-3, 0, 0),
      cancelLock: iso(-3, 10, 0),
      venue: '源深体育中心',
      opponent: '东岸竞技',
      ourSide: 'HOME',
      competition: '城市业余联赛',
      season: '2026',
      round: 8,
      status: 'FINISHED',
      capacity: 10,
      guestCapacity: 1,
      waitlistCapacity: 2,
      includeGuests: true,
      score: { home: 3, away: 2 },
      registrations: [
        reg(1, 'PLAYER', 'CONFIRMED', 5000), reg(3, 'PLAYER', 'CONFIRMED', 4900),
        reg(4, 'PLAYER', 'CONFIRMED', 4800), reg(5, 'PLAYER', 'CONFIRMED', 4700),
        reg(6, 'PLAYER', 'CONFIRMED', 4600), reg(7, 'PLAYER', 'CONFIRMED', 4500),
        reg(8, 'PLAYER', 'CONFIRMED', 4400), reg(9, 'PLAYER', 'CONFIRMED', 4300),
        reg(10, 'PLAYER', 'CONFIRMED', 4200), reg(11, 'PLAYER', 'CONFIRMED', 4100),
        reg(2, 'GUEST', 'CONFIRMED', 4000)
      ],
      attendance: [
        { playerId: 1, status: 'PRESENT', source: 'SELF' }, { playerId: 3, status: 'PRESENT', source: 'SELF' },
        { playerId: 4, status: 'PRESENT', source: 'SELF' }, { playerId: 5, status: 'PRESENT', source: 'ADMIN' },
        { playerId: 6, status: 'PRESENT', source: 'SELF' }, { playerId: 7, status: 'PRESENT', source: 'SELF' },
        { playerId: 8, status: 'PRESENT', source: 'SELF' }, { playerId: 9, status: 'ABSENT', source: 'SYSTEM' },
        { playerId: 10, status: 'PRESENT', source: 'SELF' }, { playerId: 11, status: 'PRESENT', source: 'SELF' },
        { playerId: 2, status: 'PRESENT', source: 'ADMIN' }
      ],
      events: [
        { id: 'e1', type: 'GOAL', playerId: 10, assistId: 3, minute: 12, half: 'FIRST', team: 'HOME' },
        { id: 'e2', type: 'YELLOW_CARD', playerId: 5, minute: 28, half: 'FIRST', team: 'HOME' },
        { id: 'e3', type: 'GOAL', playerId: 11, assistId: 10, minute: 44, half: 'FIRST', team: 'HOME' },
        { id: 'e4', type: 'SUBSTITUTION', inId: 13, outId: 6, minute: 60, half: 'SECOND', team: 'HOME' },
        { id: 'e5', type: 'GOAL', playerId: 10, assistId: 8, minute: 75, half: 'SECOND', team: 'HOME' },
        { id: 'e6', type: 'OWN_GOAL', playerId: 16, minute: 82, half: 'SECOND', team: 'HOME' }
      ],
      lineups: {
        MAIN: {
          formationId: '232',
          starters: { gk: 4, d1: 5, d2: 6, m1: 1, m2: 3, m3: 8, f1: 10, f2: 11 },
          bench: [7, 9]
        }
      },
      grouping: null
    },
    {
      id: 'm4',
      seasonMatchNo: 55,
      totalMatchNo: 198,
      title: '江南联 vs 云河FC',
      type: 'OFFICIAL',
      format: '8人制',
      start: iso(-10, 20, 0),
      end: iso(-10, 22, 0),
      signupDeadline: iso(-10, 0, 0),
      cancelLock: iso(-10, 10, 0),
      venue: '江湾体育场',
      opponent: '江南联',
      ourSide: 'AWAY',
      competition: '城市业余联赛',
      season: '2026',
      round: 7,
      status: 'FINISHED',
      capacity: 10,
      guestCapacity: 2,
      waitlistCapacity: 2,
      includeGuests: true,
      score: { home: 1, away: 1 },
      registrations: [
        reg(1, 'PLAYER', 'CONFIRMED', 8000), reg(3, 'PLAYER', 'CONFIRMED', 7900),
        reg(4, 'PLAYER', 'CONFIRMED', 7800), reg(5, 'PLAYER', 'CONFIRMED', 7700),
        reg(6, 'PLAYER', 'CONFIRMED', 7600), reg(7, 'PLAYER', 'CONFIRMED', 7500),
        reg(8, 'PLAYER', 'CONFIRMED', 7400), reg(9, 'PLAYER', 'CONFIRMED', 7300),
        reg(10, 'PLAYER', 'CONFIRMED', 7200)
      ],
      attendance: [
        { playerId: 1, status: 'PRESENT', source: 'SELF' }, { playerId: 3, status: 'PRESENT', source: 'SELF' },
        { playerId: 4, status: 'PRESENT', source: 'SELF' }, { playerId: 5, status: 'PRESENT', source: 'SELF' },
        { playerId: 6, status: 'ABSENT', source: 'SYSTEM' }, { playerId: 7, status: 'PRESENT', source: 'SELF' },
        { playerId: 8, status: 'PRESENT', source: 'SELF' }, { playerId: 9, status: 'PRESENT', source: 'SELF' },
        { playerId: 10, status: 'PRESENT', source: 'SELF' }
      ],
      events: [{ id: 'e7', type: 'GOAL', playerId: 3, assistId: 10, minute: 36, half: 'FIRST', team: 'AWAY' }],
      lineups: {
        MAIN: {
          formationId: '322',
          starters: { gk: 4, d1: 5, d2: 6, d3: 7, m1: 1, m2: 3, f1: 10, f2: 9 },
          bench: [8, 11]
        }
      },
      grouping: null
    },
    {
      id: 'm5',
      seasonMatchNo: 60,
      totalMatchNo: 203,
      title: '云河FC vs 校友队',
      type: 'FRIENDLY',
      format: '8人制',
      start: iso(16, 15, 0),
      end: iso(16, 17, 0),
      signupDeadline: iso(16, 0, 0),
      cancelLock: iso(16, 10, 0),
      venue: '世博球场',
      opponent: '校友队',
      ourSide: 'HOME',
      competition: '',
      season: '',
      round: null,
      status: 'SIGNUP_OPEN',
      capacity: 12,
      guestCapacity: 2,
      waitlistCapacity: 2,
      includeGuests: true,
      score: { home: null, away: null },
      registrations: [
        reg(1, 'PLAYER', 'CONFIRMED', 300), reg(3, 'PLAYER', 'CONFIRMED', 280),
        reg(4, 'PLAYER', 'CONFIRMED', 260), reg(5, 'PLAYER', 'CONFIRMED', 240),
        reg(6, 'PLAYER', 'CONFIRMED', 220), reg(8, 'PLAYER', 'CONFIRMED', 200)
      ],
      attendance: [],
      events: [],
      lineups: {},
      grouping: null
    },
    {
      id: 'm6',
      seasonMatchNo: 54,
      totalMatchNo: 197,
      title: '云河FC vs 西城FC',
      type: 'OFFICIAL',
      format: '8人制',
      start: iso(-17, 20, 0),
      end: iso(-17, 22, 0),
      signupDeadline: iso(-17, 0, 0),
      cancelLock: iso(-17, 10, 0),
      venue: '西城体育中心',
      opponent: '西城FC',
      ourSide: 'AWAY',
      competition: '城市业余联赛',
      season: '2026',
      round: 6,
      status: 'CANCELLED',
      capacity: 10,
      guestCapacity: 1,
      waitlistCapacity: 2,
      includeGuests: true,
      score: { home: null, away: null },
      registrations: [
        reg(1, 'PLAYER', 'CONFIRMED', 12000), reg(3, 'PLAYER', 'CONFIRMED', 11900),
        reg(4, 'PLAYER', 'CONFIRMED', 11800), reg(5, 'PLAYER', 'CONFIRMED', 11700),
        reg(6, 'PLAYER', 'CONFIRMED', 11600), reg(7, 'PLAYER', 'CONFIRMED', 11500),
        reg(8, 'PLAYER', 'CONFIRMED', 11400), reg(9, 'PLAYER', 'CONFIRMED', 11300)
      ],
      attendance: [],
      events: [],
      lineups: {},
      grouping: null
    },
    {
      id: 'm7',
      seasonMatchNo: 58,
      totalMatchNo: 201,
      title: '队内对抗赛',
      type: 'INTERNAL',
      format: '8人制',
      start: iso(7, 19, 0),
      end: iso(7, 21, 0),
      signupDeadline: iso(7, 0, 0),
      cancelLock: iso(7, 10, 0),
      venue: '浦东足球公园 1 号场',
      opponent: '',
      ourSide: null,
      competition: '',
      season: '',
      round: null,
      status: 'SIGNUP_OPEN',
      capacity: 40,
      guestCapacity: 0,
      waitlistCapacity: 2,
      includeGuests: false,
      score: { home: null, away: null },
      registrations: [
        reg(1, 'PLAYER', 'CONFIRMED', 700), reg(3, 'PLAYER', 'CONFIRMED', 680),
        reg(4, 'PLAYER', 'CONFIRMED', 660), reg(5, 'PLAYER', 'CONFIRMED', 640),
        reg(6, 'PLAYER', 'CONFIRMED', 620), reg(7, 'PLAYER', 'CONFIRMED', 600),
        reg(8, 'PLAYER', 'CONFIRMED', 580), reg(9, 'PLAYER', 'CONFIRMED', 560),
        reg(10, 'PLAYER', 'CONFIRMED', 540), reg(11, 'PLAYER', 'CONFIRMED', 520),
        reg(12, 'PLAYER', 'CONFIRMED', 500), reg(13, 'PLAYER', 'CONFIRMED', 480),
        reg(14, 'PLAYER', 'CONFIRMED', 460), reg(15, 'PLAYER', 'CONFIRMED', 440),
        reg(22, 'PLAYER', 'CONFIRMED', 420), reg(23, 'PLAYER', 'CONFIRMED', 400),
        reg(24, 'PLAYER', 'CONFIRMED', 380), reg(25, 'PLAYER', 'CONFIRMED', 360),
        reg(26, 'PLAYER', 'CONFIRMED', 340), reg(27, 'PLAYER', 'CONFIRMED', 320),
        reg(28, 'PLAYER', 'CONFIRMED', 300), reg(29, 'PLAYER', 'CONFIRMED', 280),
        reg(30, 'PLAYER', 'CONFIRMED', 260), reg(31, 'PLAYER', 'CONFIRMED', 240),
        reg(42, 'PLAYER', 'CONFIRMED', 220), reg(43, 'PLAYER', 'CONFIRMED', 200),
        reg(44, 'PLAYER', 'CONFIRMED', 180), reg(45, 'PLAYER', 'CONFIRMED', 160),
        reg(46, 'PLAYER', 'CONFIRMED', 140), reg(47, 'PLAYER', 'CONFIRMED', 120),
        reg(48, 'PLAYER', 'CONFIRMED', 100), reg(49, 'PLAYER', 'CONFIRMED', 80),
        reg(50, 'PLAYER', 'CONFIRMED', 60), reg(51, 'PLAYER', 'CONFIRMED', 40)
      ],
      attendance: [],
      events: [],
      lineups: {},
      grouping: null
    },
    {
      id: 'm8',
      seasonMatchNo: 53,
      totalMatchNo: 196,
      title: '海风 vs 云河FC',
      type: 'OFFICIAL',
      format: '8人制',
      start: iso(-24, 20, 0),
      end: iso(-24, 22, 0),
      signupDeadline: iso(-24, 0, 0),
      cancelLock: iso(-24, 10, 0),
      venue: '海风球场',
      opponent: '海风',
      ourSide: 'AWAY',
      competition: '城市业余联赛',
      season: '2026',
      round: 5,
      status: 'FINISHED',
      capacity: 10,
      guestCapacity: 1,
      waitlistCapacity: 2,
      includeGuests: true,
      score: { home: 4, away: 0 },
      registrations: [
        reg(1, 'PLAYER', 'CONFIRMED', 16000), reg(3, 'PLAYER', 'CONFIRMED', 15900),
        reg(4, 'PLAYER', 'CONFIRMED', 15800), reg(5, 'PLAYER', 'CONFIRMED', 15700),
        reg(6, 'PLAYER', 'CONFIRMED', 15600), reg(7, 'PLAYER', 'CONFIRMED', 15500),
        reg(8, 'PLAYER', 'CONFIRMED', 15400), reg(9, 'PLAYER', 'CONFIRMED', 15300)
      ],
      attendance: [
        { playerId: 1, status: 'PRESENT', source: 'SELF' }, { playerId: 3, status: 'PRESENT', source: 'SELF' },
        { playerId: 4, status: 'PRESENT', source: 'SELF' }, { playerId: 5, status: 'PRESENT', source: 'SELF' },
        { playerId: 6, status: 'PRESENT', source: 'SELF' }, { playerId: 7, status: 'PRESENT', source: 'SELF' },
        { playerId: 8, status: 'PRESENT', source: 'SELF' }, { playerId: 9, status: 'PRESENT', source: 'SELF' }
      ],
      events: [],
      lineups: {},
      grouping: null
    },
    {
      id: 'm9',
      seasonMatchNo: 61,
      totalMatchNo: 204,
      title: '云河FC vs 测试联队',
      type: 'FRIENDLY',
      format: '8人制',
      start: iso(18, 20, 0),
      end: iso(18, 22, 0),
      signupDeadline: iso(18, 0, 0),
      cancelLock: iso(18, 10, 0),
      venue: '聚龙足球场',
      opponent: '测试联队',
      ourSide: 'HOME',
      competition: '',
      season: '',
      round: null,
      status: 'SIGNUP_OPEN',
      capacity: 28,
      guestCapacity: 2,
      waitlistCapacity: 2,
      includeGuests: true,
      score: { home: null, away: null },
      registrations: [
        reg(1, 'PLAYER', 'CONFIRMED', 900), reg(3, 'PLAYER', 'CONFIRMED', 880),
        reg(4, 'PLAYER', 'CONFIRMED', 860), reg(5, 'PLAYER', 'CONFIRMED', 840),
        reg(6, 'PLAYER', 'CONFIRMED', 820), reg(7, 'PLAYER', 'CONFIRMED', 800),
        reg(8, 'PLAYER', 'CONFIRMED', 780), reg(9, 'PLAYER', 'CONFIRMED', 760),
        reg(10, 'PLAYER', 'CONFIRMED', 740), reg(11, 'PLAYER', 'CONFIRMED', 720),
        reg(12, 'PLAYER', 'CONFIRMED', 700), reg(13, 'PLAYER', 'CONFIRMED', 680),
        reg(14, 'PLAYER', 'CONFIRMED', 660), reg(15, 'PLAYER', 'CONFIRMED', 640),
        reg(16, 'PLAYER', 'CONFIRMED', 620), reg(17, 'PLAYER', 'CONFIRMED', 600),
        reg(22, 'PLAYER', 'CONFIRMED', 580), reg(23, 'PLAYER', 'CONFIRMED', 560),
        reg(24, 'PLAYER', 'CONFIRMED', 540), reg(25, 'PLAYER', 'CONFIRMED', 520),
        reg(26, 'PLAYER', 'CONFIRMED', 500), reg(27, 'PLAYER', 'CONFIRMED', 480),
        reg(28, 'PLAYER', 'CONFIRMED', 460), reg(29, 'PLAYER', 'CONFIRMED', 440),
        reg(30, 'PLAYER', 'CONFIRMED', 420), reg(31, 'PLAYER', 'CONFIRMED', 400),
        reg(32, 'PLAYER', 'CONFIRMED', 380), reg(33, 'PLAYER', 'CONFIRMED', 360),
        reg(2, 'GUEST', 'CONFIRMED', 340), reg(18, 'GUEST', 'CONFIRMED', 320)
      ],
      attendance: [],
      events: [],
      lineups: {},
      grouping: null
    }
  ];

  const formations = [
    { id: '232', name: '2-3-2', slots: [
      { id: 'gk', role: 'GOALKEEPER', x: 50, y: 88 },
      { id: 'd1', role: 'DEFENDER', x: 25, y: 70 }, { id: 'd2', role: 'DEFENDER', x: 75, y: 70 },
      { id: 'm1', role: 'MIDFIELDER', x: 18, y: 50 }, { id: 'm2', role: 'MIDFIELDER', x: 50, y: 50 }, { id: 'm3', role: 'MIDFIELDER', x: 82, y: 50 },
      { id: 'f1', role: 'FORWARD', x: 35, y: 24 }, { id: 'f2', role: 'FORWARD', x: 65, y: 24 }
    ] },
    { id: '322', name: '3-2-2', slots: [
      { id: 'gk', role: 'GOALKEEPER', x: 50, y: 88 },
      { id: 'd1', role: 'DEFENDER', x: 20, y: 70 }, { id: 'd2', role: 'DEFENDER', x: 50, y: 70 }, { id: 'd3', role: 'DEFENDER', x: 80, y: 70 },
      { id: 'm1', role: 'MIDFIELDER', x: 35, y: 50 }, { id: 'm2', role: 'MIDFIELDER', x: 65, y: 50 },
      { id: 'f1', role: 'FORWARD', x: 35, y: 24 }, { id: 'f2', role: 'FORWARD', x: 65, y: 24 }
    ] },
    { id: '331', name: '3-3-1', slots: [
      { id: 'gk', role: 'GOALKEEPER', x: 50, y: 88 },
      { id: 'd1', role: 'DEFENDER', x: 20, y: 70 }, { id: 'd2', role: 'DEFENDER', x: 50, y: 70 }, { id: 'd3', role: 'DEFENDER', x: 80, y: 70 },
      { id: 'm1', role: 'MIDFIELDER', x: 20, y: 50 }, { id: 'm2', role: 'MIDFIELDER', x: 50, y: 50 }, { id: 'm3', role: 'MIDFIELDER', x: 80, y: 50 },
      { id: 'f1', role: 'FORWARD', x: 50, y: 24 }
    ] },
    { id: '223', name: '2-2-3', slots: [
      { id: 'gk', role: 'GOALKEEPER', x: 50, y: 88 },
      { id: 'd1', role: 'DEFENDER', x: 30, y: 70 }, { id: 'd2', role: 'DEFENDER', x: 70, y: 70 },
      { id: 'm1', role: 'MIDFIELDER', x: 30, y: 50 }, { id: 'm2', role: 'MIDFIELDER', x: 70, y: 50 },
      { id: 'f1', role: 'FORWARD', x: 20, y: 24 }, { id: 'f2', role: 'FORWARD', x: 50, y: 24 }, { id: 'f3', role: 'FORWARD', x: 80, y: 24 }
    ] },
    { id: '313', name: '3-1-3', slots: [
      { id: 'gk', role: 'GOALKEEPER', x: 50, y: 88 },
      { id: 'd1', role: 'DEFENDER', x: 20, y: 70 }, { id: 'd2', role: 'DEFENDER', x: 50, y: 70 }, { id: 'd3', role: 'DEFENDER', x: 80, y: 70 },
      { id: 'm1', role: 'MIDFIELDER', x: 50, y: 50 },
      { id: 'f1', role: 'FORWARD', x: 20, y: 24 }, { id: 'f2', role: 'FORWARD', x: 50, y: 24 }, { id: 'f3', role: 'FORWARD', x: 80, y: 24 }
    ] },
    { id: '412', name: '4-1-2', slots: [
      { id: 'gk', role: 'GOALKEEPER', x: 50, y: 88 },
      { id: 'd1', role: 'DEFENDER', x: 15, y: 70 }, { id: 'd2', role: 'DEFENDER', x: 38, y: 70 }, { id: 'd3', role: 'DEFENDER', x: 62, y: 70 }, { id: 'd4', role: 'DEFENDER', x: 85, y: 70 },
      { id: 'm1', role: 'MIDFIELDER', x: 50, y: 50 },
      { id: 'f1', role: 'FORWARD', x: 35, y: 24 }, { id: 'f2', role: 'FORWARD', x: 65, y: 24 }
    ] },
    { id: '421', name: '4-2-1', slots: [
      { id: 'gk', role: 'GOALKEEPER', x: 50, y: 88 },
      { id: 'd1', role: 'DEFENDER', x: 15, y: 70 }, { id: 'd2', role: 'DEFENDER', x: 38, y: 70 }, { id: 'd3', role: 'DEFENDER', x: 62, y: 70 }, { id: 'd4', role: 'DEFENDER', x: 85, y: 70 },
      { id: 'm1', role: 'MIDFIELDER', x: 35, y: 50 }, { id: 'm2', role: 'MIDFIELDER', x: 65, y: 50 },
      { id: 'f1', role: 'FORWARD', x: 50, y: 24 }
    ] },
    { id: '403', name: '4-0-3', slots: [
      { id: 'gk', role: 'GOALKEEPER', x: 50, y: 88 },
      { id: 'd1', role: 'DEFENDER', x: 15, y: 70 }, { id: 'd2', role: 'DEFENDER', x: 38, y: 70 }, { id: 'd3', role: 'DEFENDER', x: 62, y: 70 }, { id: 'd4', role: 'DEFENDER', x: 85, y: 70 },
      { id: 'f1', role: 'FORWARD', x: 20, y: 24 }, { id: 'f2', role: 'FORWARD', x: 50, y: 24 }, { id: 'f3', role: 'FORWARD', x: 80, y: 24 }
    ] }
  ];

  const permissions = [
    { code: 'match:view:any', label: '查看比赛', group: '比赛' },
    { code: 'match:create:any', label: '创建比赛', group: '比赛' },
    { code: 'match:edit:any', label: '编辑/延期/重新排期比赛', group: '比赛' },
    { code: 'match:status:any', label: '变更比赛状态', group: '比赛' },
    { code: 'registration:create:own', label: '报名', group: '报名' },
    { code: 'registration:remove:any', label: '移除报名', group: '报名' },
    { code: 'attendance:checkin:own', label: '自助签到', group: '考勤' },
    { code: 'attendance:view:any', label: '查看全队考勤', group: '考勤' },
    { code: 'attendance:mark:any', label: '标记考勤', group: '考勤' },
    { code: 'event:create:own', label: '录入自己的事件', group: '事件' },
    { code: 'event:edit:own', label: '修改自己的事件', group: '事件' },
    { code: 'event:edit:any', label: '修改任意事件', group: '事件' },
    { code: 'event:delete:any', label: '删除事件', group: '事件' },
    { code: 'lineup:view:any', label: '查看阵容', group: '阵容/分组' },
    { code: 'lineup:edit:any', label: '编辑阵容', group: '阵容/分组' },
    { code: 'grouping:view:any', label: '查看分组', group: '阵容/分组' },
    { code: 'grouping:generate:any', label: '生成分组', group: '阵容/分组' },
    { code: 'player:edit:own', label: '编辑自己的资料', group: '球员/权限' },
    { code: 'player:edit:any', label: '编辑任意球员', group: '球员/权限' },
    { code: 'player:view:any', label: '查看管理端球员', group: '球员/权限' },
    { code: 'user:approve:any', label: '审核用户', group: '球员/权限' },
    { code: 'role:view:any', label: '查看角色', group: '球员/权限' },
    { code: 'role:manage:any', label: '管理角色与权限', group: '球员/权限' },
    { code: 'audit:view:any', label: '查看操作日志', group: '球员/权限' },
    { code: 'settings:view:any', label: '查看系统设置', group: '球员/权限' },
    { code: 'settings:manage:any', label: '管理系统设置', group: '球员/权限' },
    { code: 'invite:create:match', label: '创建比赛邀请', group: '邀请' },
    { code: 'invite:manage:any', label: '管理邀请', group: '邀请' }
  ];

  const guestPermissions = ['player:edit:own', 'registration:create:own', 'attendance:checkin:own'];
  const playerPermissions = guestPermissions.concat(['attendance:view:any', 'lineup:view:any', 'grouping:view:any', 'event:create:own', 'event:edit:own', 'invite:create:match']);

  const roles = [
    { code: 'GUEST', name: '外挂球员', isDefault: true, permissions: guestPermissions },
    { code: 'PLAYER', name: '正式球员', isDefault: true, permissions: playerPermissions },
    { code: 'ADMIN', name: '管理员', isDefault: true, permissions: permissions.map((p) => p.code) },
    { code: 'COACH', name: '教练', isDefault: false, permissions: ['match:view:any', 'lineup:view:any', 'grouping:view:any'] }
  ];

  const notifications = [
    { id: 'n1', userId: 3, title: '报名成功', body: '你已成功报名「云河FC vs 海港联」，赛前 2 小时可签到。', at: iso(-1, 10, 30), read: false },
    { id: 'n2', userId: 3, title: '比赛提醒', body: '「云河FC vs 海港联」还有 3 天开赛，请留意集合信息。', at: iso(0, 8, 0), read: false },
    { id: 'n3', userId: 2, title: '报名成功', body: '你已成功报名「云河FC vs 海港联」。', at: iso(0, 9, 15), read: false },
    { id: 'n4', userId: 1, title: '待审核用户', body: '有 1 位新用户等待审核。', at: iso(0, 7, 30), read: false },
    { id: 'n5', userId: 1, title: '角色变更', body: '教练角色已更新权限。', at: iso(-1, 18, 20), read: true }
  ];

  const audit = [
    { id: 'a2', actor: '管理员', action: '调整角色权限', target: 'COACH', at: iso(-1, 18, 10) },
    { id: 'a1', actor: '管理员', action: '创建比赛', target: '云河FC vs 海港联', at: iso(-1, 16, 20) },
    { id: 'a3', actor: '管理员', action: '标记考勤', target: '云河FC vs 东岸竞技', at: iso(-3, 22, 40) },
    { id: 'a4', actor: '陈子昂', action: '录入事件', target: '云河FC vs 东岸竞技', at: iso(-3, 21, 10) }
  ];

  const customFormations = [];

  const settings = {
    maxPlayers: 30,
    maxGuests: 4,
    maxWaitlist: 2,
    groupingManualWeight: 70,
    groupingAutoWeight: 30,
    groupingMinAppearances: 3,
    groupingAppearanceWeight: 50,
    groupingGoalWeight: 25,
    groupingAssistWeight: 25
  };

  window.YHFC_DATA = {
    players,
    matches,
    formations,
    permissions,
    roles,
    notifications,
    audit,
    customFormations,
    settings,
    demoUsers: [
      { id: 0, label: '未登录访客', role: 'VISITOR', name: '访客' },
      { id: 21, label: '待审核用户', role: 'PENDING', name: '待审核球员' },
      { id: 2, label: '外挂球员', role: 'GUEST', name: '林外援' },
      { id: 3, label: '正式球员', role: 'PLAYER', name: '陈子昂' },
      { id: 1, label: '管理员', role: 'ADMIN', name: '管理员' }
    ]
  };
})();
