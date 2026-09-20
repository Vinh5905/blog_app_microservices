import axios from "axios";

const userAuthAPI = axios.create({
    baseURL: "/api",
    headers: {
        "Content-Type": "application/json",
    },
});

const postAPI = axios.create({
    baseURL: "/api",
    headers: {
        "Content-Type": "application/json",
    },
});

const commentAPI = axios.create({
    baseURL: "/api",
    headers: {
        "Content-Type": "application/json",
    },
});

export { userAuthAPI, postAPI, commentAPI };
