// seeker-dashboard.js
// Improved: uses CSRF meta tags, smooth transitions, and no flash on load

document.addEventListener('DOMContentLoaded', () => {

    // === Helpers ===
    function getCsrf() {
        const tokenMeta = document.querySelector('meta[name="_csrf"]');
        const headerMeta = document.querySelector('meta[name="_csrf_header"]');
        return {
            headerName: headerMeta ? headerMeta.getAttribute('content') : null,
            token: tokenMeta ? tokenMeta.getAttribute('content') : null
        };
    }

    function formatDateShort(isoDateStr) {
        try {
            const d = new Date(isoDateStr);
            if (isNaN(d)) return isoDateStr;
            return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
        } catch (e) {
            return isoDateStr;
        }
    }

    // =============================================
    // == PREMIUM MODAL LOGIC (Auto-hide)         ==
    // =============================================
    const premiumModal = document.getElementById('premium-modal-container');
    const lockedLink = document.getElementById('nav-ai-locked');
    const closeModalBtn = document.getElementById('premium-modal-close');
    let modalTimer = null;

    if (lockedLink && premiumModal && closeModalBtn) {
        const showPremiumModal = (e) => {
            e.preventDefault();
            if (modalTimer) clearTimeout(modalTimer);
            premiumModal.classList.add('show');
            modalTimer = setTimeout(() => {
                premiumModal.classList.remove('show');
            }, 2000);
        };

        const hidePremiumModal = (e) => {
            if (e) e.preventDefault();
            if (modalTimer) clearTimeout(modalTimer);
            premiumModal.classList.remove('show');
        };

        lockedLink.addEventListener('click', showPremiumModal);
        closeModalBtn.addEventListener('click', hidePremiumModal);
        premiumModal.addEventListener('click', (e) => {
            if (e.target === premiumModal) hidePremiumModal(null);
        });
    }

    // =============================================
    // === Global Variables (from inline script) ===
    // const currentViewGlobal;
    // const activeChatsDataGlobal;

    // --- DOM Elements ---
    const views = {
        'dashboard': document.getElementById('dashboard-view'),
        'jobs': document.getElementById('jobs-view'),
        'tracker': document.getElementById('tracker-view'),
        'chats': document.getElementById('chats-view'),
        'support': document.getElementById('support-view'),
        'ai-assistant': document.getElementById('ai-assistant-view')
    };
    const initialView = typeof currentViewGlobal !== 'undefined' ? currentViewGlobal : 'dashboard';
    const activeChatsData = typeof activeChatsDataGlobal !== 'undefined' ? activeChatsDataGlobal : [];

    const employerChatList = document.getElementById('employer-chat-list');
    const employerChatMain = document.getElementById('employer-chat-main');
    const supportHistoryDiv = document.getElementById('message-history-support');
    const supportForm = document.getElementById('support-chat-form');
    const supportInput = document.getElementById('support-chat-input');

    let activeChatPoll = null;

    // =============================================
    // == 1. VIEW SWITCHER & NAVIGATION LOGIC     ==
    // =============================================

    function switchView(targetViewId, skipAnimation = false) {
        if (activeChatPoll) {
            clearInterval(activeChatPoll);
            activeChatPoll = null;
        }

        if (history.pushState) {
            let currentUrl = new URL(window.location);
            currentUrl.searchParams.set('view', targetViewId);
            currentUrl.searchParams.delete('openAppId');
            currentUrl.pathname = '/seeker/dashboard';
            history.pushState(null, null, currentUrl.toString());
        }

        const mainHeaderTitle = document.getElementById('main-header-title');
        const titles = { 'dashboard': 'Dashboard Overview', 'jobs': 'Job Search', 'tracker': 'My Applications', 'chats': 'Messages', 'support': 'Support Chat', 'ai-assistant': 'AI Job Assistant' };
        if (mainHeaderTitle) {
            mainHeaderTitle.textContent = titles[targetViewId] || 'Job Seeker Dashboard';
        }

        Object.keys(views).forEach(viewId => {
            const viewElement = views[viewId];
            if (viewElement) {
                if (viewId === targetViewId) {
                    const isFlexView = (viewId === 'chats' || viewId === 'support' || viewId === 'ai-assistant');
                    const activeClass = isFlexView ? 'active-flex' : 'active';

                    if (skipAnimation) {
                        // Immediate switch, no animation class toggling (prevents flicker)
                        viewElement.classList.remove('inactive');
                        viewElement.classList.add(activeClass);
                        viewElement.style.display = isFlexView ? 'flex' : 'block';
                        viewElement.style.transform = 'translateX(0)';
                    } else {
                        // Smooth animation for client-side navigation
                        viewElement.classList.remove('active', 'inactive', 'active-flex');
                        viewElement.style.display = isFlexView ? 'flex' : 'block';
                        setTimeout(() => {
                            viewElement.classList.add(activeClass);
                            viewElement.style.transform = 'translateX(0)';
                        }, 10);
                    }

                    if (viewId === 'dashboard' && typeof initChart !== 'undefined') {
                        initChart();
                    }

                    if (targetViewId === 'support') {
                        fetchSupportChatHistory();
                        if (activeChatPoll) clearInterval(activeChatPoll);
                        activeChatPoll = setInterval(fetchSupportChatHistory, 5000);
                    }

                } else {
                    if (skipAnimation) {
                        viewElement.classList.remove('active', 'active-flex');
                        viewElement.classList.add('inactive');
                        viewElement.style.display = 'none';
                    } else {
                        viewElement.classList.add('inactive');
                        viewElement.style.transform = 'translateX(100%)';
                        setTimeout(() => {
                            if (viewElement.classList.contains('inactive')) {
                                viewElement.style.display = 'none';
                            }
                        }, 300);
                    }
                }
            }
        });

        document.querySelectorAll('.sidebar-nav a[data-view]').forEach(link => {
            link.classList.remove('active-nav');
            if (link.getAttribute('data-view') === targetViewId) {
                link.classList.add('active-nav');
            }
        });
    }

    document.querySelectorAll('.sidebar-nav a[data-view]').forEach(link => {
        link.addEventListener('click', function(event) {
            event.preventDefault();
            const targetViewId = this.getAttribute('data-view');
            if (targetViewId) {
                switchView(targetViewId);
            }
        });
    });

    window.addEventListener('popstate', (event) => {
        const newParams = new URLSearchParams(window.location.search);
        const newView = newParams.get('view') || initialView;
        if (newView === 'jobs') {
            window.location.href = '/jobs/hub';
        } else {
            switchView(newView);
        }
    });

    // =============================================
    // == 2. APPLICATION CHAT LOGIC ("My Chats")  ==
    // =============================================

    if (employerChatList) {
        employerChatList.addEventListener('click', function(event) {
            const item = event.target.closest('.chat-list-item');
            if (!item) return;

            if (activeChatPoll) clearInterval(activeChatPoll);

            const appId = item.getAttribute('data-appid');

            document.querySelectorAll('.chat-list-item').forEach(i => i.classList.remove('active-chat'));
            item.classList.add('active-chat');

            renderApplicationChat(appId);
        });
    }

    function renderApplicationChat(appId) {
        if (activeChatPoll) clearInterval(activeChatPoll);

        const chatData = activeChatsData.find(chat => String(chat.id) === String(appId));
        if (!chatData) {
            employerChatMain.innerHTML = '<div class="chat-placeholder"><p>Error: Could not find chat data.</p></div>';
            return;
        }

        const header = `<div class="chat-main-header">Chatting with ${chatData.companyName} (for: ${chatData.jobTitle})</div>`;
        const messageHistory = `<div class="message-history" id="message-history-${appId}">Loading...</div>`;
        const inputGroup = `
            <div class="input-group" style="background: white; border-top: 1px solid #eee; padding: 1rem;">
                <form id="seeker-chat-form-${appId}" data-appid="${appId}" style="display: flex; width: 100%;">
                    <input type="text" name="message" placeholder="Type your message..." autocomplete="off" required style="flex-grow: 1; border: 1px solid #ccc; padding: 10px; border-radius: 20px; margin-right: 10px;">
                    <button type="submit" style="background-color: #007bff; color: white; border: none; padding: 10px 20px; border-radius: 20px; cursor: pointer;">Send</button>
                </form>
            </div>
        `;

        employerChatMain.innerHTML = header + messageHistory + inputGroup;
        employerChatMain.dataset.activeAppId = appId;

        attachApplicationChatFormListener(appId);
        fetchApplicationChatHistory(appId);
        activeChatPoll = setInterval(() => fetchApplicationChatHistory(appId), 5000);
    }

    function attachApplicationChatFormListener(appId) {
        const chatForm = document.getElementById(`seeker-chat-form-${appId}`);
        if (chatForm) {
            chatForm.addEventListener('submit', async (event) => {
                event.preventDefault();
                const messageInput = chatForm.querySelector('input[name="message"]');
                const message = messageInput ? messageInput.value.trim() : '';

                if (!message) return;

                try {
                    const csrf = getCsrf();
                    const headers = { 'Content-Type': 'application/json' };
                    if (csrf && csrf.headerName && csrf.token) headers[csrf.headerName] = csrf.token;

                    const response = await fetch(`/seeker/api/applications/${appId}/messages`, {
                        method: 'POST',
                        credentials: 'same-origin',
                        headers: headers,
                        body: JSON.stringify({ message: message })
                    });

                    if (response.ok) {
                        messageInput.value = '';
                        const chatHistory = document.getElementById(`message-history-${appId}`);
                        if (chatHistory) {
                            const formattedMessage = message.replace(/\n/g, '<br>');
                            chatHistory.innerHTML += `
                                <div class="message-bubble sent">
                                    <p style="margin:0;">${formattedMessage}</p>
                                    <span class="message-time">Just now</span>
                                </div>
                                <div style="clear: both;"></div>
                            `;
                            chatHistory.scrollTop = chatHistory.scrollHeight;
                        }
                    } else {
                        alert('Failed to send message.');
                    }
                } catch (error) {
                    console.error('Error sending message:', error);
                }
            });
        }
    }

    function fetchApplicationChatHistory(appId) {
        const historyDiv = document.getElementById(`message-history-${appId}`);
        if (!historyDiv) {
            if (activeChatPoll) clearInterval(activeChatPoll);
            return;
        }

        const isScrolledToBottom = historyDiv.scrollHeight - historyDiv.clientHeight <= historyDiv.scrollTop + 1;

        fetch(`/seeker/api/applications/${appId}/messages`, { credentials: 'same-origin' })
            .then(response => response.json())
            .then(messages => {
                const newContentHash = messages.length + (messages.length > 0 ? messages[messages.length - 1].createdAt : "");
                if (historyDiv.dataset.contentHash === newContentHash) return;
                historyDiv.dataset.contentHash = newContentHash;

                let html = '';
                messages.forEach(msg => {
                    const isSent = msg.senderRole === 'JOB_SEEKER';
                    const bubbleClass = isSent ? 'sent' : 'received';
                    let header = '';
                    if (!isSent) {
                        header = `<span style="font-weight: bold; font-size: 0.75rem; color: #007bff; display: block; margin-bottom: 2px;">${msg.senderFirstName || 'Employer'}:</span>`;
                    }
                    const formattedMessage = (msg.message || '').replace(/\n/g, '<br>');
                    html += `
                        <div class="message-bubble ${bubbleClass}">
                            ${header}
                            <p style="margin:0;">${formattedMessage}</p>
                            <span class="message-time">${formatDateShort(msg.createdAt || '')}</span>
                        </div>
                        <div style="clear: both;"></div>
                    `;
                });

                historyDiv.innerHTML = html || '<p class="text-center text-gray-400 p-4">No message history. Start the conversation!</p>';
                if (isScrolledToBottom) historyDiv.scrollTop = historyDiv.scrollHeight;
            })
            .catch(error => {
                historyDiv.innerHTML = '<p class="text-center text-red-500 p-4">Failed to load messages.</p>';
            });
    }


    // =============================================
    // == 3. SUPPORT CHAT LOGIC (Admin)           ==
    // =============================================

    function fetchSupportChatHistory() {
        if (!supportHistoryDiv) return;
        const isScrolledToBottom = supportHistoryDiv.scrollHeight - supportHistoryDiv.clientHeight <= supportHistoryDiv.scrollTop + 1;

        fetch(`/seeker/api/support/messages`, { credentials: 'same-origin' })
            .then(response => response.json())
            .then(messages => {
                const newContentHash = messages.length + (messages.length > 0 ? messages[messages.length-1].createdAt : "");
                if (supportHistoryDiv.dataset.contentHash === newContentHash) return;
                supportHistoryDiv.dataset.contentHash = newContentHash;

                supportHistoryDiv.innerHTML = '';
                if (!messages || messages.length === 0) {
                    supportHistoryDiv.innerHTML = '<p class="text-center text-gray-400 p-4">Welcome to Support Chat. Send a message and an Admin will reply soon.</p>';
                    return;
                }

                messages.forEach(msg => {
                    const isSent = msg.senderRole === 'JOB_SEEKER';
                    const bubbleClass = isSent ? 'sent' : 'received';
                    let header = '';
                    if (!isSent) {
                        header = `<span style="font-weight: bold; font-size: 0.75rem; color: #007bff; display: block; margin-bottom: 2px;">${msg.senderFirstName || 'Support'} ${msg.senderLastName || 'Team'}:</span>`;
                    }
                    const formattedMessage = (msg.message || '').replace(/\n/g, '<br>');
                    supportHistoryDiv.innerHTML += `
                        <div class="message-bubble ${bubbleClass}">
                            ${header}
                            <p style="margin:0;">${formattedMessage}</p>
                            <span class="message-time">${formatDateShort(msg.createdAt || '')}</span>
                        </div>
                        <div style="clear: both;"></div>
                    `;
                });

                if (isScrolledToBottom) supportHistoryDiv.scrollTop = supportHistoryDiv.scrollHeight;
            })
            .catch(error => {
                supportHistoryDiv.innerHTML = '<p class="text-center text-red-500 p-4">Failed to load support messages.</p>';
            });
    }

    if (supportForm && supportInput) {
        supportForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            const message = supportInput.value ? supportInput.value.trim() : '';
            if (!message) { alert('Message cannot be empty.'); return; }

            const csrf = getCsrf();
            const headers = { 'Content-Type': 'application/json' };
            if (csrf && csrf.headerName && csrf.token) headers[csrf.headerName] = csrf.token;

            try {
                const res = await fetch('/seeker/api/support/note/add', {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: headers,
                    body: JSON.stringify({ message: message })
                });

                if (!res.ok) { alert('Failed to send support message.'); return; }
                supportInput.value = '';
                await fetchSupportChatHistory();
            } catch (err) {
                alert('Network error. Please try again.');
            }
        });
    }

    // =============================================
    // == 4. INITIALIZER                          ==
    // =============================================

    document.addEventListener('click', (event) => {
        const targetButton = event.target.closest('.view-dialogue-btn');
        if (targetButton) {
            event.preventDefault();
            const appId = targetButton.getAttribute('data-appid');
            if (appId) {
                switchView('chats');
                renderApplicationChat(appId);
                setTimeout(() => {
                    document.querySelectorAll('.chat-list-item').forEach(i => i.classList.remove('active-chat'));
                    const chatListItem = document.querySelector(`.chat-list-item[data-appid="${appId}"]`);
                    if (chatListItem) chatListItem.classList.add('active-chat');
                }, 50);
            }
        }
    });

    window.addEventListener('load', () => {
        const urlParams = new URLSearchParams(window.location.search);
        const initialViewFromUrl = urlParams.get('view') || initialView;

        // Pass 'true' to skip animation on initial load to prevent flickering
        switchView(initialViewFromUrl, true);

        const appIdToOpen = urlParams.get('openAppId');
        if (appIdToOpen) {
            switchView('chats', true);
            const chatItem = document.querySelector(`.chat-list-item[data-appid="${appIdToOpen}"]`);
            if (chatItem) {
                document.querySelectorAll('.chat-list-item').forEach(i => i.classList.remove('active-chat'));
                chatItem.classList.add('active-chat');
                renderApplicationChat(appIdToOpen);
            }
        }

        if (initialViewFromUrl === 'support' && supportHistoryDiv) {
            setTimeout(() => { supportHistoryDiv.scrollTop = supportHistoryDiv.scrollHeight; }, 100);
        }
    });

});