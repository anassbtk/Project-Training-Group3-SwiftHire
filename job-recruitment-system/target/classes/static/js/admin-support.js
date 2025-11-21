document.addEventListener('DOMContentLoaded', () => {

    // Uses the 'adminIdGlobal' variable defined in the HTML
    const chatWindow = document.getElementById('chat-main-window');
    const adminId = adminIdGlobal;
    let activeChatPoll = null;

    document.querySelectorAll('.user-list-item').forEach(item => {
        item.addEventListener('click', () => {
            // Clear any previous polling
            if (activeChatPoll) clearInterval(activeChatPoll);

            // Highlight active user
            document.querySelectorAll('.user-list-item').forEach(i => i.classList.remove('active-chat'));
            item.classList.add('active-chat');

            const userId = item.getAttribute('data-user-id');
            const userName = item.getAttribute('data-user-name');

            renderChatComponent(userId, userName);
        });
    });

    function renderChatComponent(userId, userName) {
        chatWindow.innerHTML = `
            <div class="chat-header">
                Chatting with: ${userName} (User ID: ${userId})
            </div>
            <div class="message-history" id="message-history-${userId}">
                <p class="text-center text-gray-400 p-4">Loading messages...</p>
            </div>
            <div class="input-area">
                <form class="input-form" onsubmit="handleAdminReply(event, ${userId})">
                    <input class="message-input" id="chat-input-${userId}" placeholder="Type your reply..." required/>
                    <button type="submit">Send</button>
                </form>
            </div>
        `;

        fetchChatHistory(userId); // Fetch immediately
        activeChatPoll = setInterval(() => fetchChatHistory(userId), 5000); // Poll every 5s
    }

    function fetchChatHistory(userId) {
        const historyDiv = document.getElementById(`message-history-${userId}`);
        if (!historyDiv) {
            if (activeChatPoll) clearInterval(activeChatPoll);
            return;
        }

        const isScrolledToBottom = historyDiv.scrollHeight - historyDiv.clientHeight <= historyDiv.scrollTop + 1;

        fetch(`/admin/api/support/messages/${userId}`)
            .then(response => response.json())
            .then(messages => {
                const newContentHash = messages.length + (messages.length > 0 ? messages[messages.length-1].createdAt : "");
                if (historyDiv.dataset.contentHash === newContentHash) {
                    return; // No changes
                }
                historyDiv.dataset.contentHash = newContentHash;

                historyDiv.innerHTML = ''; // Clear
                if (messages.length === 0) {
                    historyDiv.innerHTML = '<p class="text-center text-gray-400 p-4">No message history. Start the conversation!</p>';
                    return;
                }

                messages.forEach(msg => {
                    // Admin messages are 'sent', others are 'received'
                    const isSent = msg.senderRole === 'ADMIN';
                    const bubbleClass = isSent ? 'sent' : 'received';

                    historyDiv.innerHTML += `
                        <div class="message-bubble ${bubbleClass}">
                            <p style="margin:0;">${msg.message}</p>
                            <span class="message-time">${msg.createdAt}</span>
                        </div>
                        <div style="clear: both;"></div>
                    `;
                });

                if(isScrolledToBottom) {
                    historyDiv.scrollTop = historyDiv.scrollHeight;
                }
            })
            .catch(error => {
                console.error('Error fetching chat history:', error);
                historyDiv.innerHTML = '<p class="text-center text-red-500 p-4">Failed to load messages.</p>';
            });
    }

    window.handleAdminReply = function(event, userId) {
        event.preventDefault();
        const inputElement = document.getElementById(`chat-input-${userId}`);
        const message = inputElement.value.trim();
        if (message === '') return;

        fetch(`/admin/api/support/reply/${userId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message: message })
        })
            .then(response => {
                inputElement.value = ''; // Clear input
                if (!response.ok) throw new Error('API Error on send');
                fetchChatHistory(userId); // Re-fetch immediately
            })
            .catch(error => {
                alert('Failed to send message.');
                console.error('Error sending message:', error);
            });
    };
});