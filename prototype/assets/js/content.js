(function () {
  const Y = window.YHFC;
  const $ = (selector, root) => (root || document).querySelector(selector);
  const $$ = (selector, root) => Array.from((root || document).querySelectorAll(selector));
  const esc = Y.escapeHtml;

  document.addEventListener('DOMContentLoaded', () => {
    if (document.body.dataset.page === 'content') initContent();
  });

  // 原型演示：动态与相册仅对登录的正式球员与管理员可见；不对外公开、无分享链接。
  function initContent() {
    const host = $('#content-content');
    if (!host) return;
    const params = new URLSearchParams(location.search);
    const matchFilter = params.get('matchId') || '';
    const activityFilter = params.get('activityId') || '';

    const albums = [
      {
        id: 1,
        title: '2026 秋季联赛 · 第 12 场',
        matchId: 12,
        activityId: null,
        createdBy: '队长',
        createdAt: '2026-11-20 21:30',
        photos: [
          { id: 101, label: '赛前热身', uploadedBy: '队长', url: '' },
          { id: 102, label: '首发合影', uploadedBy: '张伟', url: '' },
          { id: 103, label: '下半场远射', uploadedBy: '李强', url: '' }
        ]
      },
      {
        id: 2,
        title: '2026 年度年会',
        matchId: null,
        activityId: 2,
        createdBy: '队务',
        createdAt: '2026-11-18 20:00',
        photos: [
          { id: 201, label: '宴会厅', uploadedBy: '队务', url: '' },
          { id: 202, label: '抽奖环节', uploadedBy: '王磊', url: '' }
        ]
      },
      {
        id: 3,
        title: '球队日常',
        matchId: null,
        activityId: null,
        createdBy: '张伟',
        createdAt: '2026-11-12 19:40',
        photos: [{ id: 301, label: '训练场', uploadedBy: '张伟', url: '' }]
      }
    ];
    const posts = [
      {
        id: 1,
        author: '张伟',
        content: '今天这场太爽了，下半场那脚远射值得回看十遍。',
        albumId: 1,
        likes: 6,
        createdAt: '2026-11-20 22:10'
      },
      {
        id: 2,
        author: '李强',
        content: '下周三加练，能来的接龙。',
        albumId: null,
        likes: 3,
        createdAt: '2026-11-21 09:05'
      }
    ];

    const liked = new Set();
    let nextAlbumId = 10;
    let nextPhotoId = 400;
    let nextPostId = 10;
    let tab = matchFilter || activityFilter ? 'albums' : 'timeline';
    let openAlbumId = null;

    const role = () => Y.userRole();
    const isAdmin = () => role() === 'ADMIN';
    const me = () => Y.currentUser();

    function canView() {
      return role() === 'PLAYER' || role() === 'ADMIN';
    }

    function albumById(id) {
      return albums.find((album) => album.id === Number(id)) || null;
    }

    function visibleAlbums() {
      return albums
        .filter((album) => (!matchFilter || String(album.matchId) === matchFilter)
          && (!activityFilter || String(album.activityId) === activityFilter))
        .sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1));
    }

    function ownerLabel(album) {
      if (album.matchId) return `比赛 #${album.matchId}`;
      if (album.activityId) return `活动 #${album.activityId}`;
      return '球队通用';
    }

    function likeCount(post) {
      return post.likes + (liked.has(post.id) ? 1 : 0);
    }

    function photoTile(photo) {
      if (photo.url) {
        return `<img src="${photo.url}" alt="${esc(photo.label)}" style="width:100%;height:110px;object-fit:cover;border-radius:10px">`;
      }
      return `<div style="height:110px;border-radius:10px;background:linear-gradient(135deg,#0b1220,#1e3a5f);color:#e2e8f0;display:flex;align-items:center;justify-content:center;font-size:13px">${esc(photo.label)}</div>`;
    }

    function gateHtml() {
      if (role() === 'VISITOR') {
        return `<div class="empty-state card"><h2>登录后查看动态</h2><p class="muted">动态与相册仅对球队成员开放。</p><a class="btn btn-primary" href="login.html?redirect=${encodeURIComponent('content.html')}">去登录</a></div>`;
      }
      const tip = role() === 'PENDING' ? '账号待审核，暂不能查看动态与相册。' : '动态与相册仅正式球员与管理员可见。';
      return `<div class="empty-state card"><h2>暂无权限</h2><p class="muted">${tip}</p><a class="btn btn-ghost" href="index.html">返回首页</a></div>`;
    }

    function timelineHtml() {
      const composer = `<div class="card" style="padding:14px;margin-bottom:12px">
        <textarea class="input" id="post-content" rows="3" maxlength="2000" placeholder="说点什么，或者选一个相册分享照片…"></textarea>
        <div style="display:flex;gap:8px;margin-top:8px;align-items:center;flex-wrap:wrap">
          <select class="input input-sm" id="post-album" aria-label="引用相册">
            <option value="">不引用相册（纯文字）</option>
            ${albums.map((album) => `<option value="${album.id}">${esc(album.title)}</option>`).join('')}
          </select>
          <span class="muted small">正文 1–2000 字</span>
          <button class="btn btn-primary btn-sm" id="post-submit" style="margin-left:auto">发布</button>
        </div>
      </div>`;
      const list = posts.slice().sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1));
      if (!list.length) return composer + '<div class="empty-state card"><p>还没有动态</p></div>';
      return composer + list.map((post) => {
        const album = post.albumId ? albumById(post.albumId) : null;
        const mine = post.author === me().name;
        return `<article class="card" style="padding:14px;margin-bottom:12px">
          <div class="between"><b>${esc(post.author)}</b><span class="muted small">${esc(post.createdAt)}</span></div>
          <p style="white-space:pre-wrap;margin:8px 0">${esc(post.content)}</p>
          ${album ? `<a class="card" href="content.html?${album.matchId ? `matchId=${album.matchId}` : `activityId=${album.activityId}`}" style="display:flex;gap:10px;padding:8px;text-decoration:none;color:inherit;margin-bottom:8px">
            <div style="width:64px;height:48px;border-radius:8px;background:linear-gradient(135deg,#0b1220,#1e3a5f)"></div>
            <div><b class="small">${esc(album.title)}</b><div class="muted small">${album.photos.length} 张照片 · ${esc(ownerLabel(album))}</div></div>
          </a>` : ''}
          <div style="display:flex;gap:8px;align-items:center">
            <button class="btn btn-ghost btn-sm" data-like="${post.id}">${liked.has(post.id) ? '已赞' : '点赞'} · ${likeCount(post)}</button>
            <button class="btn btn-ghost btn-sm" data-report="post:${post.id}">举报</button>
            ${mine || isAdmin() ? `<button class="btn btn-ghost btn-sm" data-delete-post="${post.id}" style="color:#dc2626">删除</button>` : ''}
          </div>
        </article>`;
      }).join('');
    }

    function albumCardHtml(album) {
      const cover = album.photos[0];
      return `<article class="card" style="padding:14px">
        <div class="between"><b>${esc(album.title)}</b><span class="badge">${album.photos.length} 张</span></div>
        <p class="muted small">${esc(ownerLabel(album))} · 创建人 ${esc(album.createdBy)} · ${esc(album.createdAt)}</p>
        <div style="margin:8px 0">${cover ? photoTile(cover) : '<div class="muted small">暂无照片</div>'}</div>
        <div style="display:flex;gap:8px;flex-wrap:wrap">
          <button class="btn btn-primary btn-sm" data-open-album="${album.id}">查看相册</button>
          ${isAdmin() ? `<button class="btn btn-ghost btn-sm" data-delete-album="${album.id}" style="color:#dc2626">删除相册</button>` : ''}
        </div>
      </article>`;
    }

    function albumsHtml() {
      const list = visibleAlbums();
      const scope = matchFilter ? `比赛 #${matchFilter}` : activityFilter ? `活动 #${activityFilter}` : '全部相册';
      return `<div class="card" style="padding:12px;display:flex;gap:12px;align-items:center;justify-content:space-between;margin-bottom:12px">
        <span class="muted small">${esc(scope)}</span>
        <button class="btn btn-primary btn-sm" id="album-create">新建相册</button>
      </div>
      ${list.length
        ? `<div style="display:grid;gap:12px;grid-template-columns:repeat(auto-fill,minmax(280px,1fr))">${list.map(albumCardHtml).join('')}</div>`
        : '<div class="empty-state card"><p>暂无相册</p></div>'}`;
    }

    function albumDetailHtml(album) {
      const photos = album.photos.slice().sort((a, b) => a.id - b.id);
      return `<div class="between" style="margin-bottom:12px">
        <div><h2 style="margin:0">${esc(album.title)}</h2><p class="muted small">${esc(ownerLabel(album))} · ${photos.length} 张</p></div>
        <div style="display:flex;gap:8px">
          <label class="btn btn-ghost btn-sm" style="cursor:pointer">上传照片<input type="file" id="photo-upload" accept="image/jpeg,image/png,image/webp" hidden></label>
          <button class="btn btn-ghost btn-sm" data-back-album>返回相册</button>
        </div>
      </div>
      ${photos.length
        ? `<div style="display:grid;gap:10px;grid-template-columns:repeat(auto-fill,minmax(150px,1fr))">${photos.map((photo) => `<figure class="card" style="padding:8px;margin:0">
            ${photoTile(photo)}
            <figcaption class="muted small" style="margin-top:6px">${esc(photo.label)} · ${esc(photo.uploadedBy)}</figcaption>
            <div style="display:flex;gap:6px;margin-top:6px">
              ${isAdmin() ? `<button class="btn btn-ghost btn-sm" data-cover="album:${album.id}:${photo.id}">设为封面</button>` : ''}
              ${isAdmin() || photo.uploadedBy === me().name ? `<button class="btn btn-ghost btn-sm" data-delete-photo="${photo.id}" style="color:#dc2626">删除</button>` : ''}
            </div>
          </figure>`).join('')}</div>`
        : '<div class="empty-state card"><p>相册还没有照片，点右上角上传。</p></div>'}
      <p class="muted small" style="margin-top:12px">单张 ≤5 MB，单相册 ≤200 张；列表使用缩略图、查看使用原图。封面默认第一张，管理员可修改。</p>`;
    }

    function render() {
      if (!canView()) {
        host.innerHTML = `<div class="container section"><div class="page-head"><div><span class="eyebrow">CONTENT</span><h1>动态与相册</h1></div></div>${gateHtml()}</div>`;
        return;
      }
      const album = openAlbumId ? albumById(openAlbumId) : null;
      const body = album ? albumDetailHtml(album) : tab === 'timeline' ? timelineHtml() : albumsHtml();
      host.innerHTML = `<div class="container section">
        <div class="page-head"><div><span class="eyebrow">CONTENT</span><h1>动态与相册</h1><p class="muted">仅正式球员与管理员可见，不对外公开</p></div></div>
        ${album ? '' : `<div class="tabs" style="margin-bottom:12px">
          <button class="tab ${tab === 'timeline' ? 'active' : ''}" data-content-tab="timeline">时间线</button>
          <button class="tab ${tab === 'albums' ? 'active' : ''}" data-content-tab="albums">相册</button>
        </div>`}
        ${body}
      </div>`;
      bind();
    }

    function bind() {
      $$('[data-content-tab]', host).forEach((button) => button.addEventListener('click', () => {
        tab = button.dataset.contentTab;
        openAlbumId = null;
        render();
      }));
      $$('[data-open-album]', host).forEach((button) => button.addEventListener('click', () => {
        openAlbumId = Number(button.dataset.openAlbum);
        render();
      }));
      const back = $('[data-back-album]', host);
      if (back) back.addEventListener('click', () => { openAlbumId = null; render(); });
      $$('[data-like]', host).forEach((button) => button.addEventListener('click', () => {
        const id = Number(button.dataset.like);
        if (liked.has(id)) liked.delete(id); else liked.add(id);
        render();
      }));
      $$('[data-delete-post]', host).forEach((button) => button.addEventListener('click', () => {
        const id = Number(button.dataset.deletePost);
        const index = posts.findIndex((post) => post.id === id);
        if (index >= 0) posts.splice(index, 1);
        Y.toast('已删除动态');
        render();
      }));
      $$('[data-delete-album]', host).forEach((button) => button.addEventListener('click', () => {
        const id = Number(button.dataset.deleteAlbum);
        const index = albums.findIndex((album) => album.id === id);
        if (index >= 0) albums.splice(index, 1);
        Y.toast('已删除相册');
        render();
      }));
      $$('[data-report]', host).forEach((button) => button.addEventListener('click', () => {
        Y.toast('已通知管理员');
      }));
      $$('[data-delete-photo]', host).forEach((button) => button.addEventListener('click', () => {
        const id = Number(button.dataset.deletePhoto);
        const album = albumById(openAlbumId);
        if (!album) return;
        const index = album.photos.findIndex((photo) => photo.id === id);
        if (index >= 0) album.photos.splice(index, 1);
        Y.toast('已删除照片');
        render();
      }));
      $$('[data-cover]', host).forEach((button) => button.addEventListener('click', () => {
        const [, albumId, photoId] = button.dataset.cover.split(':');
        const album = albumById(albumId);
        const index = album ? album.photos.findIndex((photo) => photo.id === Number(photoId)) : -1;
        if (album && index > 0) {
          const [photo] = album.photos.splice(index, 1);
          album.photos.unshift(photo);
        }
        Y.toast('已设为封面');
        render();
      }));

      const submit = $('#post-submit', host);
      if (submit) {
        submit.addEventListener('click', () => {
          const content = ($('#post-content', host)?.value || '').trim();
          if (!content) { Y.toast('正文不能为空'); return; }
          if (content.length > 2000) { Y.toast('正文最多 2000 字'); return; }
          const albumId = Number($('#post-album', host)?.value || 0) || null;
          posts.push({ id: nextPostId++, author: me().name, content, albumId, likes: 0, createdAt: '刚刚' });
          Y.toast('已发布');
          render();
        });
      }

      const upload = $('#photo-upload', host);
      if (upload) {
        upload.addEventListener('change', () => {
          const album = albumById(openAlbumId);
          const file = upload.files && upload.files[0];
          if (!album || !file) return;
          if (album.photos.length >= 200) { Y.toast('相册已满（最多 200 张）'); return; }
          if (file.size > 5 * 1024 * 1024) { Y.toast('图片不能超过 5MB'); return; }
          album.photos.push({ id: nextPhotoId++, label: file.name, uploadedBy: me().name, url: URL.createObjectURL(file) });
          Y.toast('已上传');
          render();
        });
      }

      const create = $('#album-create', host);
      if (create) {
        create.addEventListener('click', () => {
          const modal = Y.openModal('新建相册', `<label class="muted small">相册名称
              <input class="input" id="album-title" maxlength="64" placeholder="如：2026 秋季联赛 · 第 13 场">
            </label>
            <p class="muted small" style="margin-top:8px">归属：${matchFilter ? `比赛 #${matchFilter}` : activityFilter ? `活动 #${activityFilter}` : '球队通用（可在比赛/活动详情进入时自动归属）'}</p>`,
          '<button class="btn btn-primary btn-sm" id="album-save">创建</button>');
          $('#album-save', modal).addEventListener('click', () => {
            const title = ($('#album-title', modal).value || '').trim();
            if (!title) { Y.toast('请输入相册名称'); return; }
            const album = {
              id: nextAlbumId++,
              title,
              matchId: matchFilter ? Number(matchFilter) : null,
              activityId: activityFilter ? Number(activityFilter) : null,
              createdBy: me().name,
              createdAt: '刚刚',
              photos: []
            };
            albums.push(album);
            Y.closeModal();
            Y.toast('已创建相册');
            openAlbumId = album.id;
            render();
          });
        });
      }
    }

    render();
  }
})();
