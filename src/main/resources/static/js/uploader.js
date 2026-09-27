const fileInput = document.getElementById('video-file');
const uploadButton = document.getElementById('upload-button');
const cancelButton = document.getElementById('cancel-button');
const progressWrap = document.getElementById('progress-wrap');
const progressBar = document.getElementById('progress-bar');
const statusText = document.getElementById('upload-status');
let activeUpload = null;

async function api(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        headers: {'Content-Type': 'application/json', ...(options.headers || {})}
    });
    if (!response.ok) {
        const problem = await response.json().catch(() => ({}));
        throw new Error(problem.detail || `Request failed (${response.status})`);
    }
    return response.status === 204 ? null : response.json();
}

async function retry(operation, attempts = 4) {
    for (let attempt = 1; ; attempt++) {
        try {
            return await operation();
        } catch (error) {
            if (attempt === attempts || error.name === 'AbortError') throw error;
            await new Promise(resolve => setTimeout(resolve, 500 * (2 ** (attempt - 1)) + Math.random() * 250));
        }
    }
}

async function uploadPart(file, upload, partNumber, signal, onProgress) {
    const start = (partNumber - 1) * upload.partSize;
    const body = file.slice(start, Math.min(start + upload.partSize, file.size));
    return retry(async () => {
        const [part] = await api(`/api/uploads/${upload.videoId}/parts/presign`, {
            method: 'POST', body: JSON.stringify({partNumbers: [partNumber]})
        });
        const response = await fetch(part.url, {method: 'PUT', body, signal});
        if (!response.ok) throw new Error(`Part ${partNumber} failed (${response.status})`);
        const eTag = response.headers.get('ETag');
        if (!eTag) throw new Error('S3 response did not expose the ETag header. Configure bucket CORS.');
        onProgress(body.size);
        return {partNumber, eTag};
    });
}

async function runPool(partCount, concurrency, worker) {
    let next = 1;
    const workers = Array.from({length: Math.min(concurrency, partCount)}, async () => {
        const results = [];
        while (next <= partCount) {
            const partNumber = next++;
            results.push(await worker(partNumber));
        }
        return results;
    });
    return (await Promise.all(workers)).flat().sort((a, b) => a.partNumber - b.partNumber);
}

uploadButton.addEventListener('click', async () => {
    const file = fileInput.files[0];
    if (!file) return;

    const controller = new AbortController();
    activeUpload = {controller, videoId: null};
    let uploadedBytes = 0;
    uploadButton.disabled = true;
    cancelButton.hidden = false;
    progressWrap.hidden = false;
    progressBar.style.width = '0%';

    try {
        statusText.textContent = 'Starting upload...';
        const upload = await api('/api/uploads', {
            method: 'POST',
            body: JSON.stringify({fileName: file.name, contentType: file.type, size: file.size})
        });
        activeUpload.videoId = upload.videoId;

        const parts = await runPool(upload.partCount, 3, partNumber =>
            uploadPart(file, upload, partNumber, controller.signal, bytes => {
                uploadedBytes += bytes;
                const percentage = Math.min(100, Math.round(uploadedBytes * 100 / file.size));
                progressBar.style.width = `${percentage}%`;
                statusText.textContent = `Uploading ${percentage}%`;
            }));

        statusText.textContent = 'Finalizing video...';
        await api(`/api/uploads/${upload.videoId}/complete`, {
            method: 'POST', body: JSON.stringify({parts})
        });
        statusText.textContent = 'Upload complete.';
        await loadVideos();
    } catch (error) {
        statusText.textContent = error.name === 'AbortError' ? 'Upload cancelled.' : error.message;
        statusText.classList.add('error');
    } finally {
        activeUpload = null;
        uploadButton.disabled = false;
        cancelButton.hidden = true;
    }
});

cancelButton.addEventListener('click', async () => {
    if (!activeUpload) return;
    activeUpload.controller.abort();
    if (activeUpload.videoId) {
        await fetch(`/api/uploads/${activeUpload.videoId}`, {method: 'DELETE'});
    }
});

async function loadVideos() {
    const videos = await api('/api/videos');
    const list = document.getElementById('video-list');
    const empty = document.getElementById('empty-state');
    list.replaceChildren(...videos.map(video => {
        const card = document.createElement('a');
        card.className = 'video-card';
        card.href = `/videos/${video.id}`;
        const size = (video.size / 1024 / 1024).toFixed(1);
        card.innerHTML = `<span class="play-mark">PLAY</span><strong></strong><small>${size} MiB</small>`;
        card.querySelector('strong').textContent = video.fileName;
        return card;
    }));
    empty.hidden = videos.length > 0;
}

document.getElementById('refresh-button').addEventListener('click', loadVideos);
loadVideos().catch(error => document.getElementById('empty-state').textContent = error.message);
