from django.shortcuts import render

def home_view(request):
    return render(request, 'home.html')

def search_view(request):
    return render(request, 'search.html')

def messages_view(request):
    return render(request, 'messages.html')

def profile_view(request):
    return render(request, 'profile.html')

def create_view(request):
    return render(request, 'create.html')