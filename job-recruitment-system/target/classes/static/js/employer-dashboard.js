// This file assumes that 'stats', 'employerId', 'currentViewGlobal', etc.
// have been defined in an inline script block on the HTML page.

// --- Global CSRF variables (Must be set in HTML) ---
let csrfToken = null;
let csrfHeader = null;

// 1. CHART RENDERING
const data = {
    labels: ['New (Applied)', 'Reviewed', 'Accepted', 'Hired', 'Rejected'],
    datasets: [{
        label: 'Number of Applicants',
        data: [
            stats.APPLIED,
            stats.REVIEWED,
            stats.ACCEPTED,
            stats.HIRED,
            stats.REJECTED
        ],
        backgroundColor: [
            'rgb(59, 130, 246)',
            'rgb(234, 179, 8)',
            'rgb(22, 163, 74)',
            'rgb(147, 51, 234)',
            'rgb(220, 38, 38)'
        ],
        hoverOffset: 4
    }]
};

window.onload = () => {
    const ctx = document.getElementById('applicationChart');
    if (ctx) {
        new Chart(ctx.getContext('2d'), {
            type: 'doughnut',
            data: data,
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'right',
                    }
                }
            }
        });
    }
};

// 2. VIEW SWAPPING JAVASCRIPT
document.addEventListener('DOMContentLoaded', () => {

    // Retrieve CSRF tokens from meta tags
    const csrfMeta = document.querySelector('meta[name="_csrf"]');
    const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
    if (csrfMeta && csrfHeaderMeta) {
        csrfToken = csrfMeta.content;
        csrfHeader = csrfHeaderMeta.content;
    } else {
        console.error("CSRF tokens are missing from HTML meta tags. POST requests will fail.");
    }

    // Determine if we are on the main dashboard page
    const isDashboardPage = window.location.pathname.endsWith('/dashboard') || window.location.pathname.endsWith('/dashboard/');

    const views = {
        'overview': document.getElementById('overview-view'),
        'messages': document.getElementById('inbox-view'),
        'jobs': document.getElementById('job-list-view'),
        'support': document.getElementById('support-view'),
        'candidates': document.getElementById('candidates-view')
    };
    const headerTitle = document.getElementById('main-header-title');

    // Chat-related elements
    const chatRoot = document.getElementById('react-chat-root');
    const inboxList = document.getElementById('inbox-list');
    const supportHistoryDiv = document.getElementById('message-history-support');
    const supportForm = document.getElementById('support-chat-form');
    const supportInput = document.getElementById('support-chat-input');

    let activeChatPoll = null; // Global poll timer

    // --- Main View Switcher Function ---
    function switchView(targetViewId) {

        // Only perform client-side switch if we are on the dashboard page
        if (!isDashboardPage) {
            window.location.href = `/employer/dashboard?view=${targetViewId}`;
            return;
        }

        // Clear any active chat polling when switching views
        if (activeChatPoll) {
            clearInterval(activeChatPoll);
            activeChatPoll = null;
        }

        // 1. Update URL State
        if (history.pushState) {
            let currentUrl = new URL(window.location);
            currentUrl.searchParams.set('view', targetViewId);
            currentUrl.searchParams.delete('appId');
            currentUrl.pathname = '/employer/dashboard';
            history.pushState(null, null, currentUrl.toString());
        }

        // 2. Manage View Classes
        Object.keys(views).forEach(viewId => {
            const viewElement = views[viewId];
            if (viewElement) {
                viewElement.classList.remove('active', 'inactive', 'active-flex');
                if (viewId === targetViewId) {
                    const isFlexView = (viewId === 'messages' || viewId === 'support');
                    viewElement.style.display = isFlexView ? 'flex' : 'block';

                    setTimeout(() => {
                        const activeClass = isFlexView ? 'active-flex' : 'active';
                        viewElement.classList.add(activeClass);
                        viewElement.style.transform = 'translateX(0)';
                    }, 10);

                    if (targetViewId === 'support') {
                        fetchSupportChatHistory();
                        activeChatPoll = setInterval(fetchSupportChatHistory, 5000);
                    }

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
        });

        // 3. Update Header Title
        if (headerTitle) {
            if (targetViewId === 'overview') headerTitle.textContent = 'Dashboard Overview';
            else if (targetViewId === 'messages') headerTitle.textContent = 'Applicant Inbox';
            else if (targetViewId === 'jobs') headerTitle.textContent = 'Job Postings';
            else if (targetViewId === 'support') headerTitle.textContent = 'Support Chat (Admin)';
            else if (targetViewId === 'candidates') headerTitle.textContent = 'Find Candidates';
        }

        // 4. Manage Active Nav Link
        document.querySelectorAll('.sidebar-link').forEach(link => {
            link.classList.remove('active-nav');
        });
        document.querySelectorAll('.job-filter-link').forEach(link => {
            link.classList.remove('active-all', 'active-active', 'active-pending');
        });

        const activeLink = document.querySelector(`.sidebar-link[data-view="${targetViewId}"]`);
        if (activeLink) {
            activeLink.classList.add('active-nav');
        }
    }

    // --- Event Listeners for Client-Side Navigation ---
    if (isDashboardPage) {
        document.querySelectorAll('a[data-view]').forEach(link => {
            link.addEventListener('click', function(event) {
                event.preventDefault();
                const targetViewId = this.getAttribute('data-view');
                const targetStatus = this.getAttribute('data-status');

                if (targetViewId === 'jobs' && targetStatus) {
                    window.location.href = `/employer/dashboard?view=jobs&status=${targetStatus}`;
                    return;
                }
                switchView(targetViewId);
            });
        });
    }

    // --- Chat Logic ---
    if (inboxList) {
        inboxList.addEventListener('click', function(event) {
            let item = event.target.closest('.inbox-item');
            if (!item) return;

            if (activeChatPoll) clearInterval(activeChatPoll);

            const appId = item.getAttribute('data-app-id');
            const senderName = item.getAttribute('data-sender-name');

            document.querySelectorAll('.inbox-item').forEach(i => i.classList.remove('active-chat', 'bg-blue-100'));
            item.classList.add('active-chat', 'bg-blue-100');

            renderChatComponent(appId, senderName);
        });
    }

    function renderChatComponent(appId, senderName) {
        chatRoot.innerHTML = `
            <div class="chat-container">
                <div class="message-header">
                    Chatting with: ${senderName}
                </div>
                <div class="message-history" id="message-history-${appId}">
                    <p class="text-center text-gray-400 p-4">Fetching messages for Application ${appId}...</p>
                </div>
                <div class="message-input-area">
                    <form class="message-input-form" onsubmit="handleChatSubmit(event, ${appId})">
                        <input class="message-input" id="chat-input-${appId}" placeholder="Message..." required/>
                        <button type="submit" class="send-button">
                            <i class="fa-solid fa-paper-plane"></i>
                        </button>
                    </form>
                </div>
            </div>
        `;
        fetchChatHistory(appId);
        activeChatPoll = setInterval(() => fetchChatHistory(appId), 5000);
    }

    function fetchChatHistory(appId) {
        const historyDiv = document.getElementById('message-history-' + appId);
        if (!historyDiv) {
            if (activeChatPoll) clearInterval(activeChatPoll);
            return;
        }
        const isScrolledToBottom = historyDiv.scrollHeight - historyDiv.clientHeight <= historyDiv.scrollTop + 1;
        fetch('/employer/api/applications/' + appId + '/messages')
            .then(response => response.json())
            .then(messages => {
                const newContentHash = messages.length + (messages.length > 0 ? messages[messages.length-1].createdAt : "");
                if (historyDiv.dataset.contentHash === newContentHash) return;
                historyDiv.dataset.contentHash = newContentHash;
                historyDiv.innerHTML = '';
                if (messages.length === 0) {
                    historyDiv.innerHTML = '<p class="text-center text-gray-400 p-4">No message history found. Start the conversation!</p>';
                    return;
                }
                messages.forEach(msg => {
                    const isSent = msg.senderId == employerId;
                    const bubbleClass = isSent ? 'sent' : 'received';
                    historyDiv.innerHTML += `
                        <div class="message-bubble ${bubbleClass}">
                            <p class="mb-0">${msg.message}</p>
                            <span class="message-time">${msg.createdAt}</span>
                        </div>
                        <div style="clear: both;"></div>
                    `;
                });
                if(isScrolledToBottom) historyDiv.scrollTop = historyDiv.scrollHeight;
            })
            .catch(error => {
                historyDiv.innerHTML = '<p class="text-center text-red-500 p-4">Failed to load messages.</p>';
            });
    }

    window.handleChatSubmit = function(event, appId) {
        event.preventDefault();
        const inputElement = document.getElementById('chat-input-' + appId);
        const message = inputElement.value.trim();
        if (message === '') return;
        const historyDiv = document.getElementById('message-history-' + appId);
        historyDiv.innerHTML += `
            <div class="message-bubble sent">
                <p class="mb-0">${message}</p>
                <span class="message-time">Sending...</span>
            </div>
            <div style="clear: both;"></div>
        `;
        historyDiv.scrollTop = historyDiv.scrollHeight;

        fetch('/employer/api/applications/' + appId + '/messages', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken
            },
            body: JSON.stringify({ message: message })
        })
            .then(response => {
                inputElement.value = '';
                if (!response.ok) throw new Error('API Error on send');
                fetchChatHistory(appId);
                // showNotification('Message sent successfully!', 'success');
            })
            .catch(error => {
                alert('Failed to send message.');
                fetchChatHistory(appId);
            });
    };

    // --- Support Chat Logic ---
    function fetchSupportChatHistory() {
        if (!supportHistoryDiv) return;
        const isScrolledToBottom = supportHistoryDiv.scrollHeight - supportHistoryDiv.clientHeight <= supportHistoryDiv.scrollTop + 1;
        fetch('/employer/api/support/messages')
            .then(response => response.json())
            .then(messages => {
                const newContentHash = messages.length + (messages.length > 0 ? messages[messages.length-1].createdAt : "");
                if (supportHistoryDiv.dataset.contentHash === newContentHash) return;
                supportHistoryDiv.dataset.contentHash = newContentHash;
                supportHistoryDiv.innerHTML = '';
                if (messages.length === 0) {
                    supportHistoryDiv.innerHTML = '<p class="text-center text-gray-400 p-4">Welcome to Support Chat. Send a message and an Admin will reply soon.</p>';
                    return;
                }
                messages.forEach(msg => {
                    const isSent = msg.senderRole === 'EMPLOYER';
                    const bubbleClass = isSent ? 'sent' : 'received';
                    let header = !isSent ? `<span style="font-weight: bold; font-size: 0.75rem; color: #007bff; display: block; margin-bottom: 2px;">Admin:</span>` : '';
                    supportHistoryDiv.innerHTML += `
                        <div class="message-bubble ${bubbleClass}">
                            ${header}
                            <p style="margin:0;">${msg.message}</p>
                            <span class="message-time">${msg.createdAt}</span>
                        </div>
                        <div style="clear: both;"></div>
                    `;
                });
                if(isScrolledToBottom) supportHistoryDiv.scrollTop = supportHistoryDiv.scrollHeight;
            })
            .catch(error => console.error('Error fetching support chat:', error));
    }

    if (supportForm) {
        supportForm.addEventListener('submit', (event) => {
            event.preventDefault();
            const message = supportInput.value.trim();
            if (message === '') return;

            fetch('/employer/api/support/note/add', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    [csrfHeader]: csrfToken
                },
                body: JSON.stringify({ message: message })
            })
                .then(response => {
                    supportInput.value = '';
                    if (!response.ok) throw new Error('API Error on send');
                    fetchSupportChatHistory();
                    showNotification('Support message sent!', 'success');
                })
                .catch(error => {
                    alert('Failed to send support message.');
                });
        });
    }

    // --- Initial View Setup ---
    const urlParams = new URLSearchParams(window.location.search);
    const initialView = currentViewGlobal;
    const initialStatus = currentFilterGlobal;

    if (initialView === 'messages') {
        Object.keys(views).forEach(v => { if(views[v]) views[v].style.display = 'none'; });
        views.messages.style.display = 'flex';
        views.messages.classList.add('active-flex');
        headerTitle.textContent = 'Applicant Inbox';
    } else if (initialView === 'jobs') {
        Object.keys(views).forEach(v => { if(views[v]) views[v].style.display = 'none'; });
        views.jobs.style.display = 'block';
        views.jobs.classList.add('active');
        headerTitle.textContent = 'Job Postings';
    } else if (initialView === 'support') {
        Object.keys(views).forEach(v => { if(views[v]) views[v].style.display = 'none'; });
        views.support.style.display = 'flex';
        views.support.classList.add('active-flex');
        headerTitle.textContent = 'Support Chat (Admin)';
        fetchSupportChatHistory();
        activeChatPoll = setInterval(fetchSupportChatHistory, 5000);
    } else if (initialView === 'candidates') {
        Object.keys(views).forEach(v => { if(views[v]) views[v].style.display = 'none'; });
        views.candidates.style.display = 'block';
        views.candidates.classList.add('active');
        headerTitle.textContent = 'Find Candidates';
    } else {
        Object.keys(views).forEach(v => { if(views[v]) views[v].style.display = 'none'; });
        views.overview.style.display = 'block';
        views.overview.classList.add('active');
        headerTitle.textContent = 'Dashboard Overview';
    }

    // Highlight correct initial link
    document.querySelectorAll(`.sidebar-link`).forEach(link => {
        const dataView = link.getAttribute('data-view');
        if (dataView === initialView) {
            link.classList.add('active-nav');
        }
    });

    document.querySelectorAll(`.job-filter-link`).forEach(link => {
        const dataStatus = link.getAttribute('data-status');
        if (initialView === 'jobs' && dataStatus === initialStatus) {
            link.classList.add(`active-${dataStatus.toLowerCase()}`);
        }
    });

    // Open Chat if ID provided
    const urlParamsOnLoad = new URLSearchParams(window.location.search);
    const appIdToOpen = urlParamsOnLoad.get('appId');

    if (appIdToOpen && initialView === 'messages') {
        const chatListItem = document.querySelector(`.inbox-item[data-app-id="${appIdToOpen}"]`);
        if (chatListItem) {
            setTimeout(() => {
                chatListItem.click();
            }, 100);
        }
    }
});